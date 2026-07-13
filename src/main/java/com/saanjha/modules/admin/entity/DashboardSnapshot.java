package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A periodic point-in-time rollup of platform-wide counters, so the
 * dashboard's trend lines don't require scanning every module's schema live
 * on every request. Populated by {@code AdminDashboardService.captureSnapshot},
 * invoked both on a schedule and on-demand. Deliberately denormalized flat
 * counters (not JSONB) since the metric set is small, stable, and benefits
 * from being directly queryable/sortable for trend charts.
 */
@Entity
@Table(name = "adm_dashboard_snapshots", schema = "adm")
@Getter
@Setter
public class DashboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    @Column(name = "open_reports")
    private long openReports;

    @Column(name = "pending_appeals")
    private long pendingAppeals;

    @Column(name = "active_suspensions")
    private long activeSuspensions;

    @Column(name = "moderation_actions_last_24h")
    private long moderationActionsLast24h;

    @Column(name = "high_risk_users")
    private long highRiskUsers;
}
