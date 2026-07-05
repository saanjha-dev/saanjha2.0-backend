package com.saanjha.modules.team.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit ledger — the module's answer to "who joined, who left,
 * why, who removed whom, who transferred leadership, who was the leader on
 * a given date." Rows are never updated or deleted, mirroring the pattern
 * already established by ProjectStatusLog and ApplicationStatusLog.
 */
@Entity
@Table(name = "tem_membership_history", schema = "tem")
public class MembershipHistory {

    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    public enum EventType {
        JOINED, LEFT, REMOVED, SUSPENDED, REINSTATED, ROLE_CHANGED, ARCHIVED_WITH_TEAM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "from_role", length = 20)
    private String fromRole;

    @Column(name = "to_role", length = 20)
    private String toRole;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected MembershipHistory() {
        // JPA
    }

    private MembershipHistory(UUID teamId, UUID membershipId, UUID userId, EventType eventType,
                               String fromStatus, String toStatus, String fromRole, String toRole,
                               UUID actorId, String reason) {
        this.teamId = teamId;
        this.membershipId = membershipId;
        this.userId = userId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromRole = fromRole;
        this.toRole = toRole;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }

    public static MembershipHistory statusChange(UUID teamId, UUID membershipId, UUID userId, EventType eventType,
                                                   MembershipStatus from, MembershipStatus to, UUID actorId, String reason) {
        return new MembershipHistory(teamId, membershipId, userId, eventType,
                from != null ? from.name() : null, to.name(), null, null, actorId, reason);
    }

    public static MembershipHistory roleChange(UUID teamId, UUID membershipId, UUID userId,
                                                 MembershipRole from, MembershipRole to, UUID actorId, String reason) {
        return new MembershipHistory(teamId, membershipId, userId, EventType.ROLE_CHANGED,
                null, MembershipStatus.ACTIVE.name(), from.name(), to.name(), actorId, reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getUserId() {
        return userId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getFromRole() {
        return fromRole;
    }

    public String getToRole() {
        return toRole;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
