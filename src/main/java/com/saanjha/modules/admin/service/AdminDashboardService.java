package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.dto.AdminResponseDTOs.DashboardOverviewResponse;
import com.saanjha.modules.admin.entity.AppealStatus;
import com.saanjha.modules.admin.entity.DashboardSnapshot;
import com.saanjha.modules.admin.entity.ReportStatus;
import com.saanjha.modules.admin.entity.TrustRiskLevel;
import com.saanjha.modules.admin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Admin's own read models: Dashboard, Statistics, Moderation Queue, Reports
 * Queue, Audit Timeline, Platform Metrics (Admin brief, READ MODELS
 * section). Deliberately composed from Admin's own tables only — Admin does
 * not reach into other modules' schemas for "User Overview"/"Project
 * Overview" counts; those richer overviews are named as a Future Extension
 * Point (they would need each owning module to expose a small read-only
 * summary projection, e.g. {@code ProjectService.getSnapshot}, which already
 * exists and is exactly the seam to use — see the Final Report).
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ReportRepository reportRepository;
    private final AppealRepository appealRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final AnnouncementRepository announcementRepository;
    private final TrustScoreRepository trustScoreRepository;
    private final DashboardSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        long openReports = reportRepository.countByStatus(ReportStatus.OPEN);
        long inReviewReports = reportRepository.countByStatus(ReportStatus.IN_REVIEW);
        long pendingAppeals = appealRepository.countByStatus(AppealStatus.PENDING);
        long actionsLast24h = moderationActionRepository.countByCreatedAtAfter(Instant.now().minus(24, ChronoUnit.HOURS));
        long activeAnnouncements = announcementRepository.findByStatusAndExpiresAtAfterOrExpiresAtIsNull(
                com.saanjha.modules.admin.entity.AnnouncementStatus.PUBLISHED, Instant.now()).size();
        long highRiskUsers = trustScoreRepository.countByRiskLevel(TrustRiskLevel.HIGH) + trustScoreRepository.countByRiskLevel(TrustRiskLevel.CRITICAL);

        return new DashboardOverviewResponse(openReports, inReviewReports, pendingAppeals, actionsLast24h, activeAnnouncements, highRiskUsers);
    }

    @Transactional(readOnly = true)
    public List<DashboardSnapshot> getRecentSnapshots() {
        return snapshotRepository.findTop30ByOrderByCapturedAtDesc();
    }

    /** Hourly rollup, mirroring the same "scheduled job populates a read model" pattern used elsewhere (e.g. Project's ghosting sweep). */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void captureSnapshot() {
        DashboardOverviewResponse overview = getOverview();
        DashboardSnapshot snapshot = new DashboardSnapshot();
        snapshot.setOpenReports(overview.openReports());
        snapshot.setPendingAppeals(overview.pendingAppeals());
        snapshot.setModerationActionsLast24h(overview.moderationActionsLast24h());
        snapshot.setHighRiskUsers(overview.highRiskUsers());
        snapshotRepository.save(snapshot);
    }
}
