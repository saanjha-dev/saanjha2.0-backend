package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.ModerationAction;
import com.saanjha.modules.admin.entity.ModerationActionType;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.event.AdminEvents.TeamModerationActionRecordedEvent;
import com.saanjha.modules.admin.repository.ModerationActionRepository;
import com.saanjha.modules.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Team Moderation. Unlike Project and User, Team's own module already ships
 * fully admin-capable endpoints (lock/unlock/dissolve/suspend-member/
 * reinstate-member), all gated on {@code team:moderate} — a permission
 * ROLE_ADMIN already holds (see V11 migration). This service does not
 * duplicate that logic; it is a thin pass-through that adds Admin's own
 * unified moderation-history record and audit-log entry on top of a call
 * Team already validates and executes end-to-end (locking rules, roster
 * cascade on dissolve, etc.) — exactly the "governs but never owns the
 * business logic" principle from the Admin brief, applied to a case where
 * the owning module already did the governance-readiness work itself.
 */
@Service
@RequiredArgsConstructor
public class TeamModerationService {

    private final TeamService teamService;
    private final ModerationActionRepository moderationActionRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void lockTeam(UUID actorId, UUID teamId, String reason) {
        teamService.lockTeam(teamId, actorId, reason);
        recordAndAudit(ModerationActionType.TEAM_LOCKED, actorId, teamId, reason, "TEAM_LOCKED");
    }

    @Transactional
    public void unlockTeam(UUID actorId, UUID teamId) {
        teamService.unlockTeam(teamId, actorId);
        recordAndAudit(ModerationActionType.TEAM_UNLOCKED, actorId, teamId, "Unlocked by administrator.", "TEAM_UNLOCKED");
    }

    /** Admin-only in practice — Team's own controller already restricts dissolve to {@code team:moderate}. */
    @Transactional
    public void dissolveTeam(UUID actorId, UUID teamId, String reason) {
        teamService.dissolveTeam(teamId, actorId, reason);
        recordAndAudit(ModerationActionType.TEAM_DISSOLVED, actorId, teamId, reason, "TEAM_DISSOLVED");
    }

    @Transactional
    public void suspendMember(UUID actorId, UUID teamId, UUID membershipId, String reason) {
        teamService.suspendMember(teamId, actorId, membershipId, reason);
        recordAndAudit(ModerationActionType.TEAM_MEMBER_SUSPENDED, actorId, teamId, reason, "TEAM_MEMBER_SUSPENDED");
    }

    @Transactional
    public void reinstateMember(UUID actorId, UUID teamId, UUID membershipId) {
        teamService.reinstateMember(teamId, actorId, membershipId);
        recordAndAudit(ModerationActionType.TEAM_MEMBER_REINSTATED, actorId, teamId, "Reinstated by administrator.", "TEAM_MEMBER_REINSTATED");
    }

    private void recordAndAudit(ModerationActionType type, UUID actorId, UUID teamId, String reason, String auditAction) {
        ModerationAction action = new ModerationAction();
        action.setTargetType(ModerationTargetType.TEAM);
        action.setTargetId(teamId);
        action.setActionType(type);
        action.setActorId(actorId);
        action.setReason(reason);
        moderationActionRepository.save(action);

        auditService.record(actorId, auditAction, ModerationTargetType.TEAM, teamId, null, null, reason);
        eventPublisher.publishEvent(new TeamModerationActionRecordedEvent(teamId, actorId, type.name(), reason, Instant.now()));
    }
}
