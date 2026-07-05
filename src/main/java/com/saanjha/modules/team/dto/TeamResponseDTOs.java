package com.saanjha.modules.team.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TeamResponseDTOs {

    public record TeamResponse(
            UUID id,
            UUID projectId,
            String status,
            TeamSettingsResponse settings,
            Instant activeSince,
            Instant lockedAt,
            Instant archivedAt,
            Instant dissolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TeamSettingsResponse(
            String visibility,
            boolean guestAccessEnabled,
            String activityVisibility,
            String memberInvitationPolicy
    ) {}

    public record MembershipResponse(
            UUID id,
            UUID userId,
            String role,
            String status,
            String joinedVia,
            String contributionTitle,
            Instant joinedAt,
            Instant leftAt,
            UUID removedBy,
            String removalReason
    ) {}

    public record MembershipSummaryResponse(
            UUID id,
            UUID userId,
            String role,
            String status,
            Instant joinedAt
    ) {}

    public record MembershipHistoryResponse(
            UUID id,
            UUID membershipId,
            UUID userId,
            String eventType,
            String fromStatus,
            String toStatus,
            String fromRole,
            String toRole,
            UUID actorId,
            String reason,
            Instant occurredAt
    ) {}

    /**
     * Owning member's own view — answers "am I on this team, and what can I
     * do" in one call rather than forcing the frontend to fetch the whole
     * roster and search it client-side.
     */
    public record CurrentUserMembershipResponse(
            boolean isMember,
            MembershipResponse membership
    ) {}

    /** Eventually-consistent, cheap-to-read counters — never recomputed from history on the fly. */
    public record TeamMetricsResponse(
            UUID teamId,
            int currentMemberCount,
            int formerMemberCount,
            int leadershipChangeCount,
            double averageTenureDays,
            Instant activeSince,
            /** Reserved for the future Portfolio module. Always null today — no backing column exists to leave dangling. */
            Object contributionSummary,
            /** Reserved for the future Task module. Always null today. */
            Object taskCompletionSummary
    ) {}

    /**
     * The "don't force the frontend to assemble 10 API responses" read model:
     * team + settings + current roster + who leads it, in one payload.
     */
    public record RosterViewResponse(
            TeamResponse team,
            MembershipSummaryResponse leader,
            List<MembershipSummaryResponse> members,
            long totalMembers
    ) {}

    public record TeamMutationResponse(
            String message,
            String status
    ) {}
}
