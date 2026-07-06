package com.saanjha.modules.team.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Team module. All payloads are plain value
 * records (UUID/String/Instant only, no entity references) — a deliberate
 * constraint carried over from Project/Application's event design, so these
 * remain serializable as-is if Team is ever extracted behind a message
 * broker (see the approved architecture spec, Section 16).
 *
 * Every event listed here is idempotency-safe to redeliver: consumers should
 * treat a duplicate as a no-op by checking their own already-applied state,
 * not by trusting Team to never send it twice (Team does try not to, via the
 * DB-level guards described in the V10 migration, but "the producer promises"
 * is not a substitute for "the consumer is defensive").
 *
 * Size-carrying events (MemberJoined/Left/Removed) include the authoritative
 * post-change {@code currentTeamSize} so Project's listener can perform an
 * idempotent overwrite rather than an error-prone running delta. This is
 * still order-sensitive today (an out-of-order redelivery could overwrite a
 * newer count with a stale one) — acceptable now because in-process Spring
 * events are delivered in the order Team's own pessimistically-locked
 * service produces them. Migrating to a real broker later should add a
 * monotonic sequence number to close this gap; documented here rather than
 * solved preemptively for a scenario that can't occur in this deployment.
 */
public final class TeamEvents {

    private TeamEvents() {
    }

    /** Fired once, the moment a Team is first created. Consumers: Notification, Activity (future). */
    public record TeamCreatedEvent(UUID teamId, UUID projectId, UUID founderUserId, Instant occurredAt) {}

    /** Consumers: Project (no-op today; Project already knows its own currentTeamSize needs updating via this), Notification, Chat (grant channel access), Portfolio (tenure start). */
    public record MemberJoinedEvent(UUID teamId, UUID projectId, UUID membershipId, UUID userId, String joinedVia, int currentTeamSize, Instant occurredAt) {}

    /** Voluntary departure. Consumers: Project (currentTeamSize sync), Task (unassign), Chat (revoke access), Notification. */
    public record MemberLeftEvent(UUID teamId, UUID projectId, UUID membershipId, UUID userId, int currentTeamSize, Instant occurredAt) {}

    /** Lead/Admin-initiated removal. Same consumers as MemberLeft, distinguished by {@code reason} being present. */
    public record MemberRemovedEvent(UUID teamId, UUID projectId, UUID membershipId, UUID userId, UUID removedBy, String reason, int currentTeamSize, Instant occurredAt) {}

    /** Consumers: Notification (both the outgoing and incoming Lead), Project (leadUserId cache sync — see Section 10 of the architecture spec). */
    public record LeadershipTransferredEvent(UUID teamId, UUID projectId, UUID previousLeadUserId, UUID newLeadUserId, Instant occurredAt) {}

    /** Consumers: Notification, Portfolio (role provenance). */
    public record MemberRoleChangedEvent(UUID teamId, UUID projectId, UUID userId, String fromRole, String toRole, Instant occurredAt) {}

    /**
     * FIX (TD20, architecture-review.md §9.4): the roster is in memory at the
     * exact moment this event used to fire with none of it attached — this
     * record is the "event enrichment" the module was supposed to apply here
     * from day one. Deliberately a flat value record (no entity reference),
     * same constraint as every other event payload in this class.
     */
    public record ArchivedMember(UUID userId, String role, String contributionTitle, Instant joinedAt, Instant leftAt, long tenureDays) {}

    /** Fired when the owning project reaches COMPLETED/ARCHIVED. Consumers: Chat (archive/close channel), Notification, Portfolio (finalize tenure — now possible without a cross-schema read, per the roster field). */
    public record TeamArchivedEvent(UUID teamId, UUID projectId, java.util.List<ArchivedMember> roster, Instant occurredAt) {}

    /** Consumers: Chat (pause channel activity), Notification. Reversible — see TeamUnlockedEvent. */
    public record TeamLockedEvent(UUID teamId, UUID projectId, UUID lockedBy, String reason, Instant occurredAt) {}

    public record TeamUnlockedEvent(UUID teamId, UUID projectId, UUID unlockedBy, Instant occurredAt) {}

    /** Admin-only, rare. Consumers: Notification (every affected former member), Chat (close channel), Project. */
    public record TeamDissolvedEvent(UUID teamId, UUID projectId, UUID dissolvedBy, String reason, java.util.List<ArchivedMember> roster, Instant occurredAt) {}

    /**
     * The compensating event for the last-slot overbooking race identified in
     * the architecture spec (Section 14, "Option A"). Consumers: Application/
     * Invitation, which must move the affected record back to a state that
     * prompts human reconsideration rather than silently leaving it ACCEPTED
     * with no seat.
     */
    public record MembershipCreationRejectedEvent(
            UUID projectId, UUID sourceReferenceId, String sourceType, UUID userId, String reason, Instant occurredAt
    ) {}

    public record MemberSuspendedEvent(UUID teamId, UUID projectId, UUID membershipId, UUID userId, UUID suspendedBy, String reason, Instant occurredAt) {}

    public record MemberReinstatedEvent(UUID teamId, UUID projectId, UUID membershipId, UUID userId, UUID reinstatedBy, Instant occurredAt) {}
}
