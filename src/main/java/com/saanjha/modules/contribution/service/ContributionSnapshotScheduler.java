package com.saanjha.modules.contribution.service;

import com.saanjha.modules.contribution.entity.ContributionSnapshot;
import com.saanjha.modules.contribution.repository.ContributionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * "Snapshot strategy" for scalability: rather than re-summing potentially
 * millions of ledger rows every time a trend chart needs a historical data
 * point, every contributor gets a monthly point-in-time freeze. Same
 * "one bad row must never abort the batch" discipline as every other
 * scheduled sweep in this codebase (Project's ghosting sweep, Application's
 * expiration sweep).
 */
@Component
@RequiredArgsConstructor
public class ContributionSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContributionSnapshotScheduler.class);

    private final ContributionSummaryRepository summaryRepository;
    private final ContributionService contributionService;

    /** Runs at 04:00 on the 1st of every month. */
    @Scheduled(cron = "0 0 4 1 * *")
    public void captureMonthlySnapshots() {
        var userIds = summaryRepository.findAllUserIds();
        log.info("Monthly snapshot sweep: capturing snapshots for {} contributor(s)", userIds.size());
        for (UUID userId : userIds) {
            try {
                contributionService.captureSnapshot(userId, ContributionSnapshot.Reason.SCHEDULED);
            } catch (Exception ex) {
                log.error("Failed to capture monthly snapshot for user {}", userId, ex);
            }
        }
    }
}
