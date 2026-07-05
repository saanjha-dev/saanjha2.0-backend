package com.saanjha.modules.application.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The Application module's aggregate root.
 *
 * Named {@code ProjectApplication} rather than the bare {@code Application}
 * used in the brief and in the master spec's prose. The bare name collides
 * conceptually (not just stylistically) with {@code com.saanjha.Application}
 * (the Spring Boot entry point) and with the extremely common
 * {@code org.springframework.context.ApplicationEvent} /
 * {@code ApplicationEventPublisher} types already imported throughout this
 * codebase — every file touching this entity would otherwise need an alias
 * import or a fully-qualified reference. {@code ProjectApplication} is
 * unambiguous and still immediately readable.
 *
 * Ownership is expressed via {@code applicantId}/{@code projectId}, logical
 * (non-FK) references to the User and Project modules respectively — this
 * module never joins across those schemas.
 */
@Entity
@Table(name = "app_applications", schema = "app")
@Getter
@Setter
public class ProjectApplication extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "preferred_role", length = 100)
    private String preferredRole;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(length = 50)
    private String timezone;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    /**
     * Deadline after which an open application is automatically expired by
     * the scheduled sweep (Spec: "Application deadline", "Auto expiration").
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Optimistic locking guard against concurrent review actions. */
    @Version
    @Column(nullable = false)
    private long version;

    public boolean isOpen() {
        return status == ApplicationStatus.SUBMITTED
                || status == ApplicationStatus.UNDER_REVIEW
                || status == ApplicationStatus.SHORTLISTED;
    }

    public boolean isTerminal() {
        return !isOpen();
    }
}
