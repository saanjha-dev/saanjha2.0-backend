package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The fundamental unit of trust in this module: one immutable fact, "this
 * user did verified work on this project, worth this much, in this role."
 * Modeled after {@code ContributionLedgerEntry}'s own immutability
 * discipline — no setters for anything, a private no-args constructor for
 * JPA only, and a single static factory. There is deliberately no
 * correction/reversal mechanism here (unlike the ledger): if the underlying
 * Contribution data is later corrected, that correction adjusts
 * {@code PortfolioSummary}'s live rollup, never this frozen historical
 * record — see the module write-up's Known Tradeoffs.
 *
 * Every {@code *Snapshot} field is captured ONCE, at generation time, via
 * either an enriched event payload (role/tenure, from Team's
 * {@code TeamArchivedEvent}) or the one-time {@code ProjectSnapshotProvider}
 * call (title/slug/category/technologies). A later edit to the live Project
 * row, or a later membership change, must never alter what this row says —
 * this is the module's core "never depend on mutable Project data" rule,
 * enforced structurally rather than by convention.
 */
@Entity
@Table(name = "ptf_entries", schema = "ptf", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "project_id"})
})
public class PortfolioEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    // --- Project snapshot (frozen via ProjectSnapshotProvider at generation time) ---
    @Column(name = "project_title", nullable = false, length = 150)
    private String projectTitle;

    @Column(name = "project_slug", nullable = false, length = 180)
    private String projectSlug;

    @Column(name = "project_category", nullable = false, length = 30)
    private String projectCategory;

    @Column(name = "project_description_excerpt", columnDefinition = "TEXT")
    private String projectDescriptionExcerpt;

    /** JSONB array of tag names, e.g. ["react","spring-boot"] — the honest, best-effort "technology" signal (see ProjectTag). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technologies", nullable = false, columnDefinition = "jsonb")
    private String technologiesJson = "[]";

    // --- Role/tenure snapshot (frozen via Team's TeamArchivedEvent.ArchivedMember) ---
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "was_lead", nullable = false)
    private boolean wasLead;

    @Column(name = "contribution_title", length = 255)
    private String contributionTitle;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "tenure_days")
    private Long tenureDays;

    // --- Contribution snapshot (accumulated live from ContributionRecordedEvent, frozen at generation time) ---
    @Column(name = "contribution_score", nullable = false)
    private double contributionScore;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "reviews_given", nullable = false)
    private int reviewsGiven;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.VERIFIED;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    protected PortfolioEntry() {
        // JPA
    }

    public static PortfolioEntry create(UUID userId, UUID projectId,
                                         String projectTitle, String projectSlug, String projectCategory,
                                         String projectDescriptionExcerpt, String technologiesJson,
                                         String role, boolean wasLead, String contributionTitle,
                                         Instant joinedAt, Instant leftAt, Long tenureDays,
                                         double contributionScore, int tasksCompleted, int reviewsGiven,
                                         Instant completedAt) {
        PortfolioEntry entry = new PortfolioEntry();
        entry.userId = userId;
        entry.projectId = projectId;
        entry.projectTitle = projectTitle;
        entry.projectSlug = projectSlug;
        entry.projectCategory = projectCategory;
        entry.projectDescriptionExcerpt = projectDescriptionExcerpt;
        entry.technologiesJson = technologiesJson;
        entry.role = role;
        entry.wasLead = wasLead;
        entry.contributionTitle = contributionTitle;
        entry.joinedAt = joinedAt;
        entry.leftAt = leftAt;
        entry.tenureDays = tenureDays;
        entry.contributionScore = contributionScore;
        entry.tasksCompleted = tasksCompleted;
        entry.reviewsGiven = reviewsGiven;
        entry.completedAt = completedAt;
        entry.generatedAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getProjectCategory() {
        return projectCategory;
    }

    public String getProjectDescriptionExcerpt() {
        return projectDescriptionExcerpt;
    }

    public String getTechnologiesJson() {
        return technologiesJson;
    }

    public String getRole() {
        return role;
    }

    public boolean isWasLead() {
        return wasLead;
    }

    public String getContributionTitle() {
        return contributionTitle;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public Long getTenureDays() {
        return tenureDays;
    }

    public double getContributionScore() {
        return contributionScore;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public int getReviewsGiven() {
        return reviewsGiven;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public enum VerificationStatus {
        VERIFIED
    }
}
