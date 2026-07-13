package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-submitted report against another user/project/team/message/portfolio.
 * Entry point for the Review Queue -> Decision Queue -> (Resolve|Dismiss)
 * flow, and the source of the "repeated reports" trust signal.
 *
 * {@code assignedModeratorId} is a deliberately simple single-column
 * assignment rather than a separate ModeratorAssignments table: at this
 * platform's moderator-team scale, a nullable FK-by-value on the report
 * itself carries the same information a join table would, without the extra
 * join for the one query that matters ("my queue"). Documented as a
 * simplification, not an oversight — see the Final Report's Architectural
 * Decisions section.
 */
@Entity
@Table(name = "adm_reports", schema = "adm")
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportCategory category;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "assigned_moderator_id")
    private UUID assignedModeratorId;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
