package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.Membership;
import com.saanjha.modules.team.entity.MembershipRole;
import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.modules.team.entity.Team;
import com.saanjha.modules.team.entity.TeamSettings;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resource-level authorization guard for the Team module.
 *
 * Deliberately does NOT compose {@code ProjectSecurityGuard} for the "is this
 * user the Lead" check, even though the two concepts are related: as of this
 * module, leadership is Team-owned canonical data (an ACTIVE {@code LEAD}
 * membership row), with Project's {@code leadUserId} as the synced read
 * cache — see the approved architecture spec, Section 10, Option A. Checking
 * leadership here means checking Team's own table, not delegating to
 * Project's cache, which could theoretically be one event-processing tick
 * stale immediately after a transfer.
 *
 * FIX (TD18/S12): every read endpoint in TeamController previously authorized
 * on the blanket {@code team:participate} permission — held by every
 * ROLE_USER — with no team-membership check at all, despite {@link #isMember}
 * already existing. {@link #isVisibleTo} and {@link #isMemberOfProjectsTeam}
 * are added here specifically to close that gap. See TeamService's javadoc
 * on {@code isPubliclyVisible} for why the fix is a two-tier visibility
 * check rather than a blanket {@code isMember}-everywhere clamp.
 */
@Component("teamGuard")
@RequiredArgsConstructor
public class TeamSecurityGuard {

    private static final List<MembershipStatus> LIVE_STATUSES = List.of(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED);

    private final TeamRepository teamRepository;
    private final MembershipRepository membershipRepository;
    private final TeamService teamService;

    /** True if the given user holds the ACTIVE Lead membership on this team. */
    public boolean isLeadOfTeam(UUID teamId, String userIdText) {
        if (teamId == null || userIdText == null) {
            return false;
        }
        return membershipRepository.findByTeam_IdAndRoleAndStatus(teamId, MembershipRole.LEAD, MembershipStatus.ACTIVE)
                .map(lead -> lead.getUserId().toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }

    /** True if the given user leads the team belonging to this project (no teamId in hand yet). */
    public boolean isLeadOfProjectsTeam(UUID projectId, String userIdText) {
        if (projectId == null || userIdText == null) {
            return false;
        }
        return teamRepository.findByProjectId(projectId)
                .map(Team::getId)
                .map(teamId -> isLeadOfTeam(teamId, userIdText))
                .orElse(false);
    }

    /** True if the given user currently holds any live (ACTIVE or SUSPENDED) membership on this team. */
    public boolean isMember(UUID teamId, String userIdText) {
        if (teamId == null || userIdText == null) {
            return false;
        }
        UUID userId = parseOrNull(userIdText);
        if (userId == null) {
            return false;
        }
        return membershipRepository.findByTeam_IdAndUserIdAndStatusIn(teamId, userId, LIVE_STATUSES).isPresent();
    }

    /** True if the given user holds any live membership on the team belonging to this project (no teamId in hand yet). */
    public boolean isMemberOfProjectsTeam(UUID projectId, String userIdText) {
        if (projectId == null || userIdText == null) {
            return false;
        }
        return teamRepository.findByProjectId(projectId)
                .map(Team::getId)
                .map(teamId -> isMember(teamId, userIdText))
                .orElse(false);
    }

    /**
     * True if this user should be able to view the team's roster-level
     * information: either they're a live member, or the team's own
     * {@code visibility} setting is PUBLIC. Deliberately NOT used for
     * sensitive endpoints (history, metrics, removal reasons) — see
     * TeamController's javadoc on the two-tier read authorization split.
     */
    public boolean isVisibleTo(UUID teamId, String userIdText) {
        if (teamId == null || userIdText == null) {
            return false;
        }
        return isMember(teamId, userIdText) || teamService.isPubliclyVisible(teamId);
    }

    public boolean isProjectsTeamVisibleTo(UUID projectId, String userIdText) {
        if (projectId == null || userIdText == null) {
            return false;
        }
        return teamRepository.findByProjectId(projectId)
                .map(Team::getId)
                .map(teamId -> isVisibleTo(teamId, userIdText))
                .orElse(false);
    }

    /**
     * FIX (P0-2, Invitation Policy Enforcement): centralizes the
     * {@code TeamSettings.memberInvitationPolicy} evaluation so
     * {@code InvitationController} doesn't duplicate policy logic in its
     * own {@code @PreAuthorize} expression. {@code LEAD_ONLY} requires the
     * canonical Team-owned Lead check; {@code ANY_MEMBER} accepts any live
     * (ACTIVE or SUSPENDED) member, Lead included.
     */
    public boolean canInviteToProject(UUID projectId, String userIdText) {
        if (projectId == null || userIdText == null) {
            return false;
        }
        TeamSettings.MemberInvitationPolicy policy = teamService.getInvitationPolicyForProject(projectId);
        return switch (policy) {
            case LEAD_ONLY -> isLeadOfProjectsTeam(projectId, userIdText);
            case ANY_MEMBER -> isMemberOfProjectsTeam(projectId, userIdText);
        };
    }

    /** True if the given user is the specific member the request targets (e.g. leaving their own seat). */
    public boolean isSelf(UUID membershipId, String userIdText) {
        if (membershipId == null || userIdText == null) {
            return false;
        }
        return membershipRepository.findById(membershipId)
                .map(Membership::getUserId)
                .map(userId -> userId.toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }

    private UUID parseOrNull(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
