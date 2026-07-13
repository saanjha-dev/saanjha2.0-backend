package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The domain-level moderation history record: one row per governance
 * decision (a suspension, a lock, a report resolution). This is what
 * Section D.10 ("Audit Timeline") and the per-target "Moderation History"
 * views are built from. See {@link AdminAuditLog} for the parallel
 * technical-forensics ledger these are usually written alongside.
 */
@Entity(name = "AdminModerationAction")
@Table(name = "adm_moderation_actions", schema = "adm")
@Getter
@Setter
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ModerationActionType actionType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(length = 1000)
    private String reason;

    /** Free-form evidence references (URLs, message ids, screenshots) — stored as a JSON array string. */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "related_report_id")
    private UUID relatedReportId;

    @Column(name = "reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
