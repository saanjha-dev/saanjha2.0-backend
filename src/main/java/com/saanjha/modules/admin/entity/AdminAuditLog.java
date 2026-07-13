package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The immutable, technical-level ledger of every mutating action taken
 * through the Admin module — "no admin action should ever be invisible"
 * (Admin brief, AUDIT section). Deliberately separate from
 * {@link ModerationAction}: this table captures the *mechanics* of the call
 * (who, when, request id, ip, user agent, before/after value) for security
 * forensics and compliance, while ModerationAction captures the *domain*
 * decision (why a user was suspended, linked to a report/appeal). The two
 * are usually written together in the same transaction — see
 * AdminAuditService — but they answer different questions and are queried
 * differently (this table by requestId/actor/time range; ModerationAction by
 * target).
 *
 * Insert-only by contract: {@link com.saanjha.modules.admin.repository.AdminAuditLogRepository}
 * deliberately exposes no update/delete methods. Nothing in this module ever
 * calls {@code save()} on an already-persisted row of this entity.
 */
@Entity
@Table(name = "adm_audit_log", schema = "adm")
@Getter
@Setter
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_roles", length = 255)
    private String actorRoles;

    /** Free-text action identifier, e.g. "USER_SUSPENDED", "FEATURE_FLAG_CHANGED" — deliberately a
     * String, not {@link ModerationActionType}, since audit covers config/flag/announcement changes
     * that are not moderation actions at all. */
    @Column(nullable = false, length = 100)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private ModerationTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    // Remove @Lob
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    // Remove @Lob
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 1000)
    private String reason;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
