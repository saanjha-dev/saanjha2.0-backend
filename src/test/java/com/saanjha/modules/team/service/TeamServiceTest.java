package com.saanjha.modules.team.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.modules.team.dto.TeamResponseDTOs.TeamResponse;
import com.saanjha.modules.team.entity.*;
import com.saanjha.modules.team.repository.MembershipHistoryRepository;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.repository.TeamRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipHistoryRepository historyRepository;
    @Mock private ProjectService projectService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TeamService teamService;

    private UUID projectId;
    private UUID leadUserId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, membershipRepository, historyRepository, projectService, eventPublisher, new ObjectMapper());
        projectId = UUID.randomUUID();
        leadUserId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        lenient().when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========================================================================
    // TEAM FORMATION
    // ========================================================================

    @Test
    void getOrCreateTeam_whenAlreadyExists_isIdempotentNoOp() {
        when(teamRepository.existsByProjectId(projectId)).thenReturn(true);

        teamService.getOrCreateTeam(projectId, leadUserId);

        verify(teamRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getOrCreateTeam_seedsTeamAndFoundingLead() {
        when(teamRepository.existsByProjectId(projectId)).thenReturn(false);

        teamService.getOrCreateTeam(projectId, leadUserId);

        verify(teamRepository).save(any(Team.class));
        verify(membershipRepository).save(argThat(m -> m.getRole() == MembershipRole.LEAD && m.getUserId().equals(leadUserId)));
        verify(eventPublisher).publishEvent(any());
    }

    // ========================================================================
    // ADD MEMBER
    // ========================================================================

    @Test
    void addMember_duplicateSourceReference_isIdempotentNoOp() {
        UUID applicationId = UUID.randomUUID();
        when(membershipRepository.existsBySourceReferenceId(applicationId)).thenReturn(true);

        teamService.addMember(projectId, UUID.randomUUID(), MembershipSource.APPLICATION, applicationId);

        verify(teamRepository, never()).findWithLockByProjectId(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void addMember_toLockedTeam_publishesRejectionEvent() {
        Team team = activeTeam();
        team.setStatus(TeamStatus.LOCKED);
        when(teamRepository.findWithLockByProjectId(projectId)).thenReturn(Optional.of(team));

        teamService.addMember(projectId, UUID.randomUUID(), MembershipSource.APPLICATION, UUID.randomUUID());

        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("MembershipCreationRejectedEvent")));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void addMember_whenTeamAtCapacity_publishesRejectionEvent() {
        Team team = activeTeam();
        team.setCurrentMemberCount(5);
        when(teamRepository.findWithLockByProjectId(projectId)).thenReturn(Optional.of(team));
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(projectService.getSnapshot(projectId)).thenReturn(new ProjectSnapshot(projectId, leadUserId, "IN_PROGRESS", "PUBLIC", 5, 5));

        teamService.addMember(projectId, UUID.randomUUID(), MembershipSource.INVITATION, UUID.randomUUID());

        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("MembershipCreationRejectedEvent")));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void addMember_happyPath_incrementsCountAndPublishesJoinedEvent() {
        Team team = activeTeam();
        team.setCurrentMemberCount(1);
        when(teamRepository.findWithLockByProjectId(projectId)).thenReturn(Optional.of(team));
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(projectService.getSnapshot(projectId)).thenReturn(new ProjectSnapshot(projectId, leadUserId, "IN_PROGRESS", "PUBLIC", 5, 1));

        UUID newMemberId = UUID.randomUUID();
        teamService.addMember(projectId, newMemberId, MembershipSource.APPLICATION, UUID.randomUUID());

        assertThat(team.getCurrentMemberCount()).isEqualTo(2);
        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("MemberJoinedEvent")));
    }

    @Test
    void addMember_alreadyLiveMember_isIdempotentNoOp() {
        Team team = activeTeam();
        when(teamRepository.findWithLockByProjectId(projectId)).thenReturn(Optional.of(team));
        Membership existing = new Membership();
        existing.setUserId(leadUserId);
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(existing));

        teamService.addMember(projectId, leadUserId, MembershipSource.APPLICATION, UUID.randomUUID());

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ========================================================================
    // LEADERSHIP TRANSFER
    // ========================================================================

    @Test
    void transferLeadership_toSelf_isRejected() {
        assertThatThrownBy(() -> teamService.transferLeadership(teamId, leadUserId, leadUserId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void transferLeadership_toNonActiveMember_isRejected() {
        Team team = activeTeam();
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));
        Membership currentLead = leadMembership(team, leadUserId);
        when(membershipRepository.findByTeam_IdAndRoleAndStatus(teamId, MembershipRole.LEAD, MembershipStatus.ACTIVE)).thenReturn(Optional.of(currentLead));
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), any(), eq(List.of(MembershipStatus.ACTIVE)))).thenReturn(Optional.empty());

        UUID targetUserId = UUID.randomUUID();
        assertThatThrownBy(() -> teamService.transferLeadership(teamId, leadUserId, targetUserId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void transferLeadership_happyPath_swapsRolesAndPublishesEvent() {
        Team team = activeTeam();
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));
        Membership currentLead = leadMembership(team, leadUserId);
        when(membershipRepository.findByTeam_IdAndRoleAndStatus(teamId, MembershipRole.LEAD, MembershipStatus.ACTIVE)).thenReturn(Optional.of(currentLead));

        UUID targetUserId = UUID.randomUUID();
        Membership targetMembership = new Membership();
        targetMembership.setId(UUID.randomUUID());
        targetMembership.setUserId(targetUserId);
        targetMembership.setRole(MembershipRole.MEMBER);
        targetMembership.setStatus(MembershipStatus.ACTIVE);
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(targetUserId), eq(List.of(MembershipStatus.ACTIVE))))
                .thenReturn(Optional.of(targetMembership));

        TeamResponse response = teamService.transferLeadership(teamId, leadUserId, targetUserId);

        assertThat(currentLead.getRole()).isEqualTo(MembershipRole.MEMBER);
        assertThat(targetMembership.getRole()).isEqualTo(MembershipRole.LEAD);
        assertThat(response.id()).isEqualTo(teamId);
        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("LeadershipTransferredEvent")));
    }

    // ========================================================================
    // REMOVAL
    // ========================================================================

    @Test
    void removeMember_theLead_isRejected() {
        Team team = activeTeam();
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));
        Membership lead = leadMembership(team, leadUserId);
        when(membershipRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> teamService.removeMember(teamId, UUID.randomUUID(), lead.getId(), "test"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ========================================================================
    // LEAVING
    // ========================================================================

    @Test
    void leaveTeam_soleLead_isRejectedWithArchiveGuidance() {
        Team team = activeTeam();
        team.setCurrentMemberCount(1);
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));
        Membership lead = leadMembership(team, leadUserId);
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(leadUserId), any())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> teamService.leaveTeam(teamId, leadUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Archive the project");
    }

    @Test
    void leaveTeam_leadWithOthers_mustTransferFirst() {
        Team team = activeTeam();
        team.setCurrentMemberCount(3);
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));
        Membership lead = leadMembership(team, leadUserId);
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(leadUserId), any())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> teamService.leaveTeam(teamId, leadUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Transfer leadership");
    }

    @Test
    void leaveTeam_regularMember_succeeds() {
        Team team = activeTeam();
        team.setCurrentMemberCount(2);
        when(teamRepository.findWithLockById(teamId)).thenReturn(Optional.of(team));

        UUID memberId = UUID.randomUUID();
        Membership member = new Membership();
        member.setId(UUID.randomUUID());
        member.setUserId(memberId);
        member.setRole(MembershipRole.MEMBER);
        member.setStatus(MembershipStatus.ACTIVE);
        member.setJoinedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(memberId), any())).thenReturn(Optional.of(member));

        teamService.leaveTeam(teamId, memberId);

        assertThat(member.getStatus()).isEqualTo(MembershipStatus.LEFT);
        assertThat(team.getCurrentMemberCount()).isEqualTo(1);
        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("MemberLeftEvent")));
    }

    // ========================================================================
    // P0-1: Workspace/Team Discovery
    // ========================================================================

    @Test
    void getMyWorkspaces_returnsEveryLiveMembership_mappedWithTeamAndRole() {
        Team team = activeTeam();
        Membership membership = leadMembership(team, leadUserId);
        Pageable pageable = PageRequest.of(0, 20);
        when(membershipRepository.findByUserIdAndStatusIn(eq(leadUserId), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(membership)));

        Page<com.saanjha.modules.team.dto.TeamResponseDTOs.MyTeamMembershipResponse> result =
                teamService.getMyWorkspaces(leadUserId, pageable);

        assertThat(result.getContent()).hasSize(1);
        var row = result.getContent().get(0);
        assertThat(row.team().id()).isEqualTo(teamId);
        assertThat(row.team().projectId()).isEqualTo(projectId);
        assertThat(row.role()).isEqualTo("LEAD");
        assertThat(row.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(row.membershipId()).isEqualTo(membership.getId());
    }

    @Test
    void getMyWorkspaces_returnsEmptyPage_whenUserHasNoLiveMemberships() {
        Pageable pageable = PageRequest.of(0, 20);
        when(membershipRepository.findByUserIdAndStatusIn(eq(leadUserId), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<com.saanjha.modules.team.dto.TeamResponseDTOs.MyTeamMembershipResponse> result =
                teamService.getMyWorkspaces(leadUserId, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    // ========================================================================
    // P0-2: Invitation Policy Enforcement
    // ========================================================================

    @Test
    void getInvitationPolicyForProject_defaultsToLeadOnly_whenNoTeamExistsYet() {
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThat(teamService.getInvitationPolicyForProject(projectId))
                .isEqualTo(TeamSettings.MemberInvitationPolicy.LEAD_ONLY);
    }

    @Test
    void getInvitationPolicyForProject_defaultsToLeadOnly_whenSettingsAreBlank() {
        Team team = activeTeam(); // settingsJson == "{}"
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));

        assertThat(teamService.getInvitationPolicyForProject(projectId))
                .isEqualTo(TeamSettings.MemberInvitationPolicy.LEAD_ONLY);
    }

    @Test
    void getInvitationPolicyForProject_readsAnyMember_whenConfigured() throws Exception {
        Team team = activeTeam();
        TeamSettings settings = new TeamSettings(
                TeamSettings.RosterVisibility.PUBLIC, false,
                TeamSettings.ActivityVisibility.MEMBERS_ONLY, TeamSettings.MemberInvitationPolicy.ANY_MEMBER);
        team.setSettingsJson(new ObjectMapper().writeValueAsString(settings));
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));

        assertThat(teamService.getInvitationPolicyForProject(projectId))
                .isEqualTo(TeamSettings.MemberInvitationPolicy.ANY_MEMBER);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Team activeTeam() {
        Team team = new Team();
        team.setId(teamId);
        team.setProjectId(projectId);
        team.setStatus(TeamStatus.ACTIVE);
        team.setSettingsJson("{}");
        team.setCurrentMemberCount(1);
        return team;
    }

    private Membership leadMembership(Team team, UUID userId) {
        Membership lead = new Membership();
        lead.setId(UUID.randomUUID());
        lead.setTeam(team);
        lead.setUserId(userId);
        lead.setRole(MembershipRole.LEAD);
        lead.setStatus(MembershipStatus.ACTIVE);
        lead.setJoinedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return lead;
    }
}
