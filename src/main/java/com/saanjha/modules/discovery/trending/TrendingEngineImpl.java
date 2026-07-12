package com.saanjha.modules.discovery.trending;

import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.discovery.entity.*;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.TechnologyStatRepository;
import com.saanjha.modules.discovery.repository.TrendingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Batch recompute over Discovery's own {@code dsc} tables (never Project's
 * or User's) for each entity type the brief asks Trending to cover:
 * Projects, Developers, Teams (via their project's popularity), and
 * Technologies (already rolled up by {@code TechnologyProjectionService}).
 * Each recompute replaces the previous batch for that (type, window) pair
 * in the same transaction it writes the new one, so a reader never sees a
 * half-written ranking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingEngineImpl implements TrendingEngine {

    private static final int MAX_SNAPSHOT_SIZE = 50;

    private final ProjectSearchDocumentRepository projectRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final TechnologyStatRepository technologyStatRepository;
    private final TrendingSnapshotRepository trendingSnapshotRepository;
    private final DiscoveryMetrics metrics;

    @Override
    @Transactional
    public void recompute(TrendingWindow window) {
        long start = System.currentTimeMillis();
        recomputeProjects(window);
        recomputeDevelopers(window);
        recomputeTechnologies(window);
        metrics.recordTrendingRebuildDuration(window.name(), System.currentTimeMillis() - start);
        log.info("Discovery: trending recompute complete for window {}.", window);
    }

    @Override
    public List<TrendingSnapshot> getTrending(TrendingEntityType entityType, TrendingWindow window, int limit) {
        return trendingSnapshotRepository.findByEntityTypeAndWindowOrderByRankAsc(entityType, window).stream()
                .limit(limit)
                .toList();
    }

    private void recomputeProjects(TrendingWindow window) {
        List<ProjectSearchDocument> ranked = projectRepository.findAll().stream()
                .filter(ProjectSearchDocument::isIndexed)
                .sorted(Comparator.comparingDouble(ProjectSearchDocument::getPopularityScore).reversed())
                .limit(MAX_SNAPSHOT_SIZE)
                .toList();

        replaceSnapshot(TrendingEntityType.PROJECT, window, ranked, ProjectSearchDocument::getProjectId,
                ProjectSearchDocument::getPopularityScore);
    }

    private void recomputeDevelopers(TrendingWindow window) {
        List<DeveloperSearchDocument> ranked = developerRepository.findAll().stream()
                .filter(d -> !d.isDeleted())
                .sorted(Comparator.comparingDouble(DeveloperSearchDocument::getContributionTotalScore).reversed())
                .limit(MAX_SNAPSHOT_SIZE)
                .toList();

        replaceSnapshot(TrendingEntityType.DEVELOPER, window, ranked, DeveloperSearchDocument::getUserId,
                DeveloperSearchDocument::getContributionTotalScore);
    }

    private void recomputeTechnologies(TrendingWindow window) {
        List<TechnologyStat> ranked = technologyStatRepository
                .findAllByOrderByTrendingScoreDesc(PageRequest.of(0, MAX_SNAPSHOT_SIZE));

        trendingSnapshotRepository.deleteByEntityTypeAndWindow(TrendingEntityType.TECHNOLOGY, window);
        List<TrendingSnapshot> snapshots = new ArrayList<>();
        int rank = 1;
        for (TechnologyStat stat : ranked) {
            TrendingSnapshot snapshot = new TrendingSnapshot();
            snapshot.setEntityType(TrendingEntityType.TECHNOLOGY);
            snapshot.setEntityKey(stat.getTechnologyName());
            snapshot.setWindow(window);
            snapshot.setScore(stat.getTrendingScore());
            snapshot.setRank(rank++);
            snapshots.add(snapshot);
        }
        trendingSnapshotRepository.saveAll(snapshots);
    }

    private <T> void replaceSnapshot(TrendingEntityType entityType, TrendingWindow window, List<T> ranked,
                                      java.util.function.Function<T, java.util.UUID> idExtractor,
                                      java.util.function.ToDoubleFunction<T> scoreExtractor) {
        trendingSnapshotRepository.deleteByEntityTypeAndWindow(entityType, window);
        List<TrendingSnapshot> snapshots = new ArrayList<>();
        int rank = 1;
        for (T item : ranked) {
            TrendingSnapshot snapshot = new TrendingSnapshot();
            snapshot.setEntityType(entityType);
            snapshot.setEntityKey(idExtractor.apply(item).toString());
            snapshot.setWindow(window);
            snapshot.setScore(scoreExtractor.applyAsDouble(item));
            snapshot.setRank(rank++);
            snapshots.add(snapshot);
        }
        trendingSnapshotRepository.saveAll(snapshots);
    }
}
