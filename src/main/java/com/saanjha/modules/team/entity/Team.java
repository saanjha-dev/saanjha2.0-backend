package com.saanjha.modules.team.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The Team module's aggregate root — one row per Project, created the moment
 * that project publishes (see the module's Javadoc on why: Team self-seeds
 * from {@code ProjectPublishedEvent} rather than requiring Project to emit a
 * new event just for Team's benefit).
 *
 * Settings are stored as a raw JSON string ({@code settingsJson}) rather than
 * typed columns, specifically so new settings can be introduced later without
 * a migration. {@link TeamSettings} is the typed contract on top of it —
 * callers should never read/write {@code settingsJson} directly; go through
 * {@code TeamService}'s (de)serialization instead.
 *
 * Metrics fields are denormalized counters, updated incrementally at the
 * point of each transition (O(1) per write) rather than recomputed from
 * {@code MembershipHistory} on every read.
 */
@Entity
@Table(name = "tem_teams", schema = "tem")
@Getter
@Setter
public class Team extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamStatus status = TeamStatus.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";

    @Column(name = "current_member_count", nullable = false)
    private int currentMemberCount = 0;

    @Column(name = "former_member_count", nullable = false)
    private int formerMemberCount = 0;

    @Column(name = "leadership_change_count", nullable = false)
    private int leadershipChangeCount = 0;

    @Column(name = "average_tenure_days", nullable = false)
    private double averageTenureDays = 0.0;

    @Column(name = "active_since")
    private Instant activeSince;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "dissolved_at")
    private Instant dissolvedAt;

    @Column(name = "dissolution_reason", length = 500)
    private String dissolutionReason;

    /** Pessimistic-lock target for every roster mutation; see the migration's comment for why. */
    @Version
    @Column(nullable = false)
    private long version;

    public boolean acceptsRosterChanges() {
        return status == TeamStatus.CREATED || status == TeamStatus.ACTIVE;
    }

    /**
     * Incrementally folds one member's completed tenure into the running
     * average using the standard streaming-average update — O(1), no scan
     * over history. Called exactly once per membership reaching a terminal
     * status (LEFT/REMOVED), never on read.
     */
    public void recordCompletedTenure(long tenureDays) {
        int newFormerCount = this.formerMemberCount + 1;
        this.averageTenureDays = this.averageTenureDays + ((double) tenureDays - this.averageTenureDays) / newFormerCount;
        this.formerMemberCount = newFormerCount;
    }
}
