package com.saanjha.modules.discovery.trending;

import com.saanjha.modules.discovery.entity.TrendingWindow;
import com.saanjha.modules.discovery.projection.TechnologyProjectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The only place Discovery runs on a timer, following the same
 * "one bad run must never wedge the module" discipline as every other
 * scheduled sweep in this codebase (Project's ghosting sweep, Contribution's
 * snapshot job, Notification's retry sweep).
 *
 * Technology rollups are recomputed first, every run, because the DAILY
 * trending recompute's technology ranking reads directly from
 * {@code dsc_technology_stats}.
 */
@Component
@RequiredArgsConstructor
public class TrendingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrendingScheduler.class);

    private final TechnologyProjectionService technologyProjectionService;
    private final TrendingEngine trendingEngine;

    /** Runs at 03:00 daily. */
    @Scheduled(cron = "0 0 3 * * *")
    public void recomputeDaily() {
        try {
            technologyProjectionService.recomputeAll();
            trendingEngine.recompute(TrendingWindow.DAILY);
        } catch (Exception e) {
            log.error("Discovery: daily trending recompute failed.", e);
        }
    }

    /** Runs at 03:30 every Sunday. */
    @Scheduled(cron = "0 30 3 * * SUN")
    public void recomputeWeekly() {
        try {
            trendingEngine.recompute(TrendingWindow.WEEKLY);
        } catch (Exception e) {
            log.error("Discovery: weekly trending recompute failed.", e);
        }
    }
}
