package com.saanjha.modules.team.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.modules.team.dto.TeamRequestDTOs.UpdateSettingsRequest;
import com.saanjha.modules.team.dto.TeamResponseDTOs.*;
import com.saanjha.modules.team.entity.*;
import com.saanjha.modules.team.entity.MembershipHistory.EventType;
import com.saanjha.modules.team.event.TeamEvents.*;
import com.saanjha.modules.team.repository.MembershipHistoryRepository;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.repository.TeamRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the Team aggregate: lifecycle, roster mutation, leadership
 * transfer, and the read models the frontend needs. Every project-state
 * check goes through {@code ProjectService.getSnapshot()} — the sanctioned
 * service-interface call — never a direct read of Project's schema.
 *
 * Two classes of methods, with different failure philosophies:
 *  - Listener-triggered internal methods (getOrCreateTeam, addMember,
 *    activateTeam, archiveWithTeam) treat an already-applied state as a
 *    silent no-op — duplicate event delivery must never throw or corrupt state.
 *  - API-triggered methods (transferLeadership, removeMember, etc.) throw a
 *    clear AppException on an illegal request — a direct caller deserves an
 *    explicit error, not a silent no-op.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final List<MembershipStatus> LIVE_STATUSES = List.of(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED);

    private final TeamRepository teamRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipHistoryRepository historyRepository;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // TEAM FORMATION (listener-triggered, idempotent)
    // ========================================================================

    /** Self-seeds a Team + founding LEAD membership. Safe to call more than once for the same project. */
    @Transactional
    public void getOrCreateTeam(UUID projectId, UUID founderUserId) {
        if (teamRepository.existsByProjectId(projectId)) {
            return; // Duplicate ProjectPublishedEvent delivery — already seeded.
        }

        Team team = new Team();
        team.setProjectId(projectId);
        team.setStatus(TeamStatus.CREATED);
        team.setSettingsJson(writeSettings(TeamSettings.defaults()));
        team.setCurrentMemberCount(1);

        try {
            team = teamRepository.save(team);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race against a concurrent duplicate delivery to the unique
            // index on project_id — the other delivery won, nothing left to do.
            return;
        }

        Membership founder = new Membership();
        founder.setTeam(team);
        founder.setUserId(founderUserId);
        founder.setRole(MembershipRole.LEAD);
        founder.setStatus(MembershipStatus.ACTIVE);
        founder.setJoinedVia(MembershipSource.MANUAL);
        founder = membershipRepository.save(founder);

        historyRepository.save(MembershipHistory.statusChange(
                team.getId(), founder.getId(), founderUserId, EventType.JOINED, null, MembershipStatus.ACTIVE, founderUserId, "Founding Lead."));

        eventPublisher.publishEvent(new TeamCreatedEvent(team.getId(), projectId, founderUserId, Instant.now()));
    }

    // ========================================================================
    // ROSTER GROWTH (listener-triggered, idempotent)
    // ========================================================================

    @Transactional
    public void addMember(UUID projectId, UUID userId, MembershipSource source, UUID sourceReferenceId) {
        if (sourceReferenceId != null && membershipRepository.existsBySourceReferenceId(sourceReferenceId)) {
            return; // Duplicate ApplicationAccepted/InvitationAccepted delivery — already seated.
        }

        Team team = teamRepository.findWithLockByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "No team exists yet for project " + projectId + " — this should be impossible if Project's own state machine was respected."));

        if (!team.acceptsRosterChanges()) {
            rejectMembershipCreation(projectId, sourceReferenceId, source, userId,
                    "Team is " + team.getStatus() + " and is not accepting new members.");
            return;
        }

        if (membershipRepository.findByTeam_IdAndUserIdAndStatusIn(team.getId(), userId, LIVE_STATUSES).isPresent()) {
            return; // Already a live member — treat as an idempotent no-op rather than a duplicate error.
        }

        // Re-check capacity live, under this transaction's lock on Team — never
        // trust an earlier pre-check performed by Application/Invitation.
        ProjectSnapshot project = projectService.getSnapshot(projectId);
        if (team.getCurrentMemberCount() >= project.maxTeamSize()) {
            rejectMembershipCreation(projectId, sourceReferenceId, source, userId,
                    "This project's team is already at capacity.");
            return;
        }

        Membership membership = new Membership();
        membership.setTeam(team);
        membership.setUserId(userId);
        membership.setRole(MembershipRole.MEMBER);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedVia(source);
        membership.setSourceReferenceId(sourceReferenceId);
        membership = membershipRepository.save(membership);

        team.setCurrentMemberCount(team.getCurrentMemberCount() + 1);
        team = teamRepository.save(team);

        historyRepository.save(MembershipHistory.statusChange(
                team.getId(), membership.getId(), userId, EventType.JOINED, null, MembershipStatus.ACTIVE, userId, "Joined via " + source + "."));

        eventPublisher.publishEvent(new MemberJoinedEvent(
                team.getId(), projectId, membership.getId(), userId, source.name(), team.getCurrentMemberCount(), Instant.now()));
    }

    private void rejectMembershipCreation(UUID projectId, UUID sourceReferenceId, MembershipSource source, UUID userId, String reason) {
        String sourceType = source == MembershipSource.INVITATION ? "INVITATION" : "APPLICATION";
        eventPublisher.publishEvent(new MembershipCreationRejectedEvent(projectId, sourceReferenceId, sourceType, userId, reason, Instant.now()));
    }

    // ========================================================================
    // TEAM LIFECYCLE (listener-triggered, idempotent)
    // ========================================================================

    @Transactional
    public void activateTeam(UUID projectId) {
        Optional<Team> maybeTeam = teamRepository.findWithLockByProjectId(projectId);
        if (maybeTeam.isEmpty() || maybeTeam.get().getStatus() != TeamStatus.CREATED) {
            return; // No team yet, or already activated — safe no-op either way.
        }
        Team team = maybeTeam.get();
        team.setStatus(TeamStatus.ACTIVE);
        team.setActiveSince(Instant.now());
        teamRepository.save(team);
    }

    @Transactional
    public void archiveWithTeam(UUID projectId) {
        Optional<Team> maybeTeam = teamRepository.findWithLockByProjectId(projectId);
        if (maybeTeam.isEmpty()) {
            return; // Project archived before it ever published — no team was ever created.
        }
        Team team = maybeTeam.get();
        if (team.getStatus() == TeamStatus.ARCHIVED || team.getStatus() == TeamStatus.DISSOLVED) {
            return; // Already terminal — safe no-op.
        }

        archiveAllLiveMemberships(team, MembershipHistory.SYSTEM_ACTOR_ID, "Project reached a terminal state.");

        team.setStatus(TeamStatus.ARCHIVED);
        team.setArchivedAt(Instant.now());
        teamRepository.save(team);

        eventPublisher.publishEvent(new TeamArchivedEvent(team.getId(), projectId, Instant.now()));
    }

    private void archiveAllLiveMemberships(Team team, UUID actorId, String reason) {
        List<Membership> live = membershipRepository.findByTeam_IdAndStatusIn(team.getId(), LIVE_STATUSES);
        for (Membership membership : live) {
            MembershipStatus from = membership.getStatus();
            membership.setStatus(MembershipStatus.ARCHIVED);
            team.recordCompletedTenure(tenureDays(membership));
            membershipRepository.save(membership);
            historyRepository.save(MembershipHistory.statusChange(
                    team.getId(), membership.getId(), membership.getUserId(), EventType.ARCHIVED_WITH_TEAM, from, MembershipStatus.ARCHIVED, actorId, reason));
        }
        team.setCurrentMemberCount(0);
    }

    // ========================================================================
    // LEADERSHIP TRANSFER (API-triggered)
    // ========================================================================

    @Transactional
    public TeamResponse transferLeadership(UUID teamId, UUID actingUserId, UUID newLeadUserId) {
        if (actingUserId.equals(newLeadUserId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "You cannot transfer leadership to yourself.");
        }

        Team team = lockTeamOrThrow(teamId);
        assertAcceptsRosterChanges(team);

        Membership currentLead = membershipRepository.findByTeam_IdAndRoleAndStatus(teamId, MembershipRole.LEAD, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "This team currently has no active Lead — cannot transfer."));
        if (!currentLead.getUserId().equals(actingUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the current Lead can transfer leadership.");
        }

        Membership newLead = membershipRepository.findByTeam_IdAndUserIdAndStatusIn(teamId, newLeadUserId, List.of(MembershipStatus.ACTIVE))
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED,
                        "The target user must be an active (not suspended, left, or removed) member of this team."));

        currentLead.setRole(MembershipRole.MEMBER);
        newLead.setRole(MembershipRole.LEAD);
        membershipRepository.save(currentLead);
        membershipRepository.save(newLead);

        team.setLeadershipChangeCount(team.getLeadershipChangeCount() + 1);
        teamRepository.save(team);

        historyRepository.save(MembershipHistory.roleChange(teamId, currentLead.getId(), currentLead.getUserId(), MembershipRole.LEAD, MembershipRole.MEMBER, actingUserId, "Leadership transferred to another member."));
        historyRepository.save(MembershipHistory.roleChange(teamId, newLead.getId(), newLead.getUserId(), MembershipRole.MEMBER, MembershipRole.LEAD, actingUserId, "Leadership transferred from previous Lead."));

        eventPublisher.publishEvent(new LeadershipTransferredEvent(teamId, team.getProjectId(), actingUserId, newLeadUserId, Instant.now()));

        return mapToResponse(team);
    }

    // ========================================================================
    // REMOVAL / LEAVING / SUSPENSION (API-triggered)
    // ========================================================================

    /** Convenience overload matching the master spec's literal DELETE /v1/teams/{id}/members/{uId} contract (a user id, not a membership id). */
    @Transactional
    public TeamResponse removeMemberByUserId(UUID teamId, UUID actingUserId, UUID targetUserId, String reason) {
        Membership target = membershipRepository.findByTeam_IdAndUserIdAndStatusIn(teamId, targetUserId, LIVE_STATUSES)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "This user is not currently a member of this team."));
        return removeMember(teamId, actingUserId, target.getId(), reason);
    }

    @Transactional
    public TeamResponse removeMember(UUID teamId, UUID actingUserId, UUID targetMembershipId, String reason) {
        Team team = lockTeamOrThrow(teamId);
        assertAcceptsRosterChanges(team);

        Membership target = getLiveMembershipOrThrow(teamId, targetMembershipId);
        if (target.isLead()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Transfer leadership to someone else before removing the Lead.");
        }

        MembershipStatus from = target.getStatus();
        MembershipStatusTransitionValidator.assertLegal(from, MembershipStatus.REMOVED);

        target.setStatus(MembershipStatus.REMOVED);
        target.setRemovedBy(actingUserId);
        target.setRemovalReason(reason);
        membershipRepository.save(target);

        team.setCurrentMemberCount(Math.max(team.getCurrentMemberCount() - 1, 0));
        team.recordCompletedTenure(tenureDays(target));
        teamRepository.save(team);

        historyRepository.save(MembershipHistory.statusChange(teamId, target.getId(), target.getUserId(), EventType.REMOVED, from, MembershipStatus.REMOVED, actingUserId, reason));
        eventPublisher.publishEvent(new MemberRemovedEvent(teamId, team.getProjectId(), target.getId(), target.getUserId(), actingUserId, reason, team.getCurrentMemberCount(), Instant.now()));

        return mapToResponse(team);
    }

    @Transactional
    public TeamResponse leaveTeam(UUID teamId, UUID actingUserId) {
        Team team = lockTeamOrThrow(teamId);
        assertAcceptsRosterChanges(team);

        Membership self = membershipRepository.findByTeam_IdAndUserIdAndStatusIn(teamId, actingUserId, LIVE_STATUSES)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "You are not currently a member of this team."));

        if (self.isLead()) {
            if (team.getCurrentMemberCount() > 1) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Transfer leadership to another member before leaving.");
            }
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "You are the sole member of this team. Archive the project instead of leaving — leaving would strand the project leaderless.");
        }

        MembershipStatus from = self.getStatus();
        self.setStatus(MembershipStatus.LEFT);
        self.setLeftAt(Instant.now());
        membershipRepository.save(self);

        team.setCurrentMemberCount(Math.max(team.getCurrentMemberCount() - 1, 0));
        team.recordCompletedTenure(tenureDays(self));
        teamRepository.save(team);

        historyRepository.save(MembershipHistory.statusChange(teamId, self.getId(), actingUserId, EventType.LEFT, from, MembershipStatus.LEFT, actingUserId, null));
        eventPublisher.publishEvent(new MemberLeftEvent(teamId, team.getProjectId(), self.getId(), actingUserId, team.getCurrentMemberCount(), Instant.now()));

        return mapToResponse(team);
    }

    @Transactional
    public TeamResponse suspendMember(UUID teamId, UUID actingUserId, UUID targetMembershipId, String reason) {
        Team team = lockTeamOrThrow(teamId);
        assertAcceptsRosterChanges(team);

        Membership target = getLiveMembershipOrThrow(teamId, targetMembershipId);
        if (target.isLead()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "The Lead cannot be suspended — transfer leadership first if needed.");
        }

        MembershipStatusTransitionValidator.assertLegal(target.getStatus(), MembershipStatus.SUSPENDED);
        target.setStatus(MembershipStatus.SUSPENDED);
        membershipRepository.save(target);

        historyRepository.save(MembershipHistory.statusChange(teamId, target.getId(), target.getUserId(), EventType.SUSPENDED, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED, actingUserId, reason));
        eventPublisher.publishEvent(new MemberSuspendedEvent(teamId, team.getProjectId(), target.getId(), target.getUserId(), actingUserId, reason, Instant.now()));

        return mapToResponse(team);
    }

    @Transactional
    public TeamResponse reinstateMember(UUID teamId, UUID actingUserId, UUID targetMembershipId) {
        Team team = lockTeamOrThrow(teamId);
        assertAcceptsRosterChanges(team);

        Membership target = getLiveMembershipOrThrow(teamId, targetMembershipId);
        MembershipStatusTransitionValidator.assertLegal(target.getStatus(), MembershipStatus.ACTIVE);
        target.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(target);

        historyRepository.save(MembershipHistory.statusChange(teamId, target.getId(), target.getUserId(), EventType.REINSTATED, MembershipStatus.SUSPENDED, MembershipStatus.ACTIVE, actingUserId, null));
        eventPublisher.publishEvent(new MemberReinstatedEvent(teamId, team.getProjectId(), target.getId(), target.getUserId(), actingUserId, Instant.now()));

        return mapToResponse(team);
    }

    // ========================================================================
    // TEAM-LEVEL LIFECYCLE ACTIONS (API-triggered)
    // ========================================================================

    @Transactional
    public TeamResponse lockTeam(UUID teamId, UUID actingUserId, String reason) {
        Team team = lockTeamOrThrow(teamId);
        TeamStatusTransitionValidator.assertLegal(team.getStatus(), TeamStatus.LOCKED);
        team.setStatus(TeamStatus.LOCKED);
        team.setLockedAt(Instant.now());
        team = teamRepository.save(team);

        eventPublisher.publishEvent(new TeamLockedEvent(teamId, team.getProjectId(), actingUserId, reason, Instant.now()));
        return mapToResponse(team);
    }

    @Transactional
    public TeamResponse unlockTeam(UUID teamId, UUID actingUserId) {
        Team team = lockTeamOrThrow(teamId);
        TeamStatusTransitionValidator.assertLegal(team.getStatus(), TeamStatus.ACTIVE);
        team.setStatus(TeamStatus.ACTIVE);
        team.setLockedAt(null);
        team = teamRepository.save(team);

        eventPublisher.publishEvent(new TeamUnlockedEvent(teamId, team.getProjectId(), actingUserId, Instant.now()));
        return mapToResponse(team);
    }

    /** Admin-only (enforced at the controller layer) — no Lead can dissolve their own team. */
    @Transactional
    public TeamResponse dissolveTeam(UUID teamId, UUID actingUserId, String reason) {
        Team team = lockTeamOrThrow(teamId);
        TeamStatusTransitionValidator.assertLegal(team.getStatus(), TeamStatus.DISSOLVED);

        archiveAllLiveMemberships(team, actingUserId, "Team dissolved: " + reason);

        team.setStatus(TeamStatus.DISSOLVED);
        team.setDissolvedAt(Instant.now());
        team.setDissolutionReason(reason);
        team = teamRepository.save(team);

        eventPublisher.publishEvent(new TeamDissolvedEvent(teamId, team.getProjectId(), actingUserId, reason, Instant.now()));
        return mapToResponse(team);
    }

    // ========================================================================
    // SETTINGS
    // ========================================================================

    @Transactional
    public TeamResponse updateSettings(UUID teamId, UpdateSettingsRequest request) {
        Team team = getTeamEntityOrThrow(teamId);
        TeamSettings current = readSettings(team.getSettingsJson());

        TeamSettings updated = new TeamSettings(
                request.visibility() != null ? TeamSettings.RosterVisibility.valueOf(request.visibility()) : current.visibility(),
                request.guestAccessEnabled() != null ? request.guestAccessEnabled() : current.guestAccessEnabled(),
                request.activityVisibility() != null ? TeamSettings.ActivityVisibility.valueOf(request.activityVisibility()) : current.activityVisibility(),
                request.memberInvitationPolicy() != null ? TeamSettings.MemberInvitationPolicy.valueOf(request.memberInvitationPolicy()) : current.memberInvitationPolicy()
        );

        team.setSettingsJson(writeSettings(updated));
        team = teamRepository.save(team);
        return mapToResponse(team);
    }

    // ========================================================================
    // READS / READ MODELS
    // ========================================================================

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        return mapToResponse(getTeamEntityOrThrow(teamId));
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeamByProject(UUID projectId) {
        return teamRepository.findByProjectId(projectId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "No team exists yet for this project."));
    }

    @Transactional(readOnly = true)
    public Page<MembershipSummaryResponse> getRoster(UUID teamId, MembershipStatus statusFilter, Pageable pageable) {
        List<MembershipStatus> statuses = statusFilter != null ? List.of(statusFilter) : LIVE_STATUSES;
        return membershipRepository.findByTeam_IdAndStatusIn(teamId, statuses, pageable).map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public Page<MembershipHistoryResponse> getHistory(UUID teamId, Pageable pageable) {
        return historyRepository.findByTeamIdOrderByOccurredAtDesc(teamId, pageable).map(this::mapHistoryToResponse);
    }

    @Transactional(readOnly = true)
    public List<MembershipHistoryResponse> getMembershipHistory(UUID membershipId) {
        return historyRepository.findByMembershipIdOrderByOccurredAtAsc(membershipId).stream()
                .map(this::mapHistoryToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamMetricsResponse getMetrics(UUID teamId) {
        Team team = getTeamEntityOrThrow(teamId);
        return new TeamMetricsResponse(
                team.getId(), team.getCurrentMemberCount(), team.getFormerMemberCount(),
                team.getLeadershipChangeCount(), team.getAverageTenureDays(), team.getActiveSince(),
                null, null // Reserved for Portfolio/Task — always null until those modules exist.
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserMembershipResponse getCurrentUserMembership(UUID teamId, UUID userId) {
        return membershipRepository.findByTeam_IdAndUserIdAndStatusIn(teamId, userId, LIVE_STATUSES)
                .map(m -> new CurrentUserMembershipResponse(true, mapToDetailResponse(m)))
                .orElse(new CurrentUserMembershipResponse(false, null));
    }

    @Transactional(readOnly = true)
    public RosterViewResponse getRosterView(UUID teamId) {
        Team team = getTeamEntityOrThrow(teamId);
        List<Membership> live = membershipRepository.findByTeam_IdAndStatusIn(teamId, LIVE_STATUSES);

        MembershipSummaryResponse leader = live.stream()
                .filter(Membership::isLead)
                .findFirst()
                .map(this::mapToSummary)
                .orElse(null);

        List<MembershipSummaryResponse> members = live.stream().map(this::mapToSummary).toList();

        return new RosterViewResponse(mapToResponse(team), leader, members, live.size());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Team lockTeamOrThrow(UUID teamId) {
        return teamRepository.findWithLockById(teamId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Team not found."));
    }

    private Team getTeamEntityOrThrow(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Team not found."));
    }

    private Membership getLiveMembershipOrThrow(UUID teamId, UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Membership not found."));
        if (!membership.getTeam().getId().equals(teamId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Membership not found on this team.");
        }
        if (!membership.isLive()) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "This membership is already " + membership.getStatus() + ".");
        }
        return membership;
    }

    private void assertAcceptsRosterChanges(Team team) {
        if (!team.acceptsRosterChanges()) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "This team is " + team.getStatus() + " and cannot be modified.");
        }
    }

    private long tenureDays(Membership membership) {
        Instant end = membership.getLeftAt() != null ? membership.getLeftAt() : Instant.now();
        return Math.max(Duration.between(membership.getJoinedAt(), end).toDays(), 0);
    }

    private String writeSettings(TeamSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize team settings.");
        }
    }

    private TeamSettings readSettings(String json) {
        try {
            if (json == null || json.isBlank() || json.equals("{}")) {
                return TeamSettings.defaults();
            }
            return objectMapper.readValue(json, TeamSettings.class);
        } catch (Exception ex) {
            return TeamSettings.defaults();
        }
    }

    private TeamResponse mapToResponse(Team team) {
        TeamSettings settings = readSettings(team.getSettingsJson());
        return new TeamResponse(
                team.getId(), team.getProjectId(), team.getStatus().name(),
                new TeamSettingsResponse(settings.visibility().name(), settings.guestAccessEnabled(),
                        settings.activityVisibility().name(), settings.memberInvitationPolicy().name()),
                team.getActiveSince(), team.getLockedAt(), team.getArchivedAt(), team.getDissolvedAt(),
                team.getCreatedAt(), team.getUpdatedAt()
        );
    }

    private MembershipSummaryResponse mapToSummary(Membership membership) {
        return new MembershipSummaryResponse(
                membership.getId(), membership.getUserId(), membership.getRole().name(),
                membership.getStatus().name(), membership.getJoinedAt());
    }

    private MembershipResponse mapToDetailResponse(Membership membership) {
        return new MembershipResponse(
                membership.getId(), membership.getUserId(), membership.getRole().name(), membership.getStatus().name(),
                membership.getJoinedVia().name(), membership.getContributionTitle(), membership.getJoinedAt(),
                membership.getLeftAt(), membership.getRemovedBy(), membership.getRemovalReason());
    }

    private MembershipHistoryResponse mapHistoryToResponse(MembershipHistory history) {
        return new MembershipHistoryResponse(
                history.getId(), history.getMembershipId(), history.getUserId(), history.getEventType().name(),
                history.getFromStatus(), history.getToStatus(), history.getFromRole(), history.getToRole(),
                history.getActorId(), history.getReason(), history.getOccurredAt());
    }
}
