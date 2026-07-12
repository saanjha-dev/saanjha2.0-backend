package com.saanjha.modules.discovery.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.discovery.entity.RecommendationCacheEntry;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.repository.RecommendationCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cache-aside over whichever {@link RecommendationStrategy} matches the
 * request's {@link RecommendationType}. Request-time reads hit the cache
 * first (Section "Performance": "Never perform expensive joins at request
 * time" applies just as much to skill-overlap scans); a miss computes once
 * and writes through. {@code RecommendationScheduler}-style proactive
 * refresh is a documented future extension, not implemented in this pass --
 * cache-aside-on-read is a correct, if less proactive, starting point.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEngineImpl implements RecommendationEngine {

    private static final int CACHE_TTL_MINUTES = 30;

    private final List<RecommendationStrategy> strategies;
    private final RecommendationCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final DiscoveryMetrics metrics;

    @Override
    @Transactional
    public RecommendationResult recommend(RecommendationRequest request) {
        long start = System.currentTimeMillis();
        try {
            return doRecommend(request);
        } finally {
            metrics.recordRecommendationLatency(request.type().name(), System.currentTimeMillis() - start);
        }
    }

    private RecommendationResult doRecommend(RecommendationRequest request) {
        // Context-scoped requests (TEAMMATES for a specific project) are never cached under
        // the (userId, type) key alone -- the same Lead may ask this question for several
        // different projects, and caching by userId+type only would silently return a stale
        // answer for the wrong project.
        if (request.contextProjectId() == null) {
            var cached = cacheRepository.findByUserIdAndRecommendationType(request.userId(), request.type());
            if (cached.isPresent() && cached.get().getExpiresAt().isAfter(Instant.now())) {
                metrics.incrementCacheHit(request.type().name());
                return new RecommendationResult(deserialize(cached.get().getPayload()), true);
            }
            metrics.incrementCacheMiss(request.type().name());
        }

        RecommendationStrategy strategy = strategies.stream()
                .filter(s -> s.supports() == request.type())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No RecommendationStrategy registered for type " + request.type()));

        RecommendationResult result = strategy.recommend(request);

        if (request.contextProjectId() == null) {
            persistCache(request, result);
        }
        return result;
    }

    private void persistCache(RecommendationRequest request, RecommendationResult result) {
        try {
            RecommendationCacheEntry entry = cacheRepository
                    .findByUserIdAndRecommendationType(request.userId(), request.type())
                    .orElseGet(RecommendationCacheEntry::new);
            entry.setUserId(request.userId());
            entry.setRecommendationType(request.type());
            entry.setPayload(objectMapper.writeValueAsString(result.items()));
            entry.setGeneratedAt(Instant.now());
            entry.setExpiresAt(Instant.now().plus(CACHE_TTL_MINUTES, ChronoUnit.MINUTES));
            cacheRepository.save(entry);
        } catch (Exception e) {
            log.warn("Discovery: failed to cache recommendation result for user {} type {}.",
                    request.userId(), request.type(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<RecommendationResult.Item> deserialize(String payload) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(payload, List.class);
            return raw.stream().map(m -> new RecommendationResult.Item(
                    m.get("entityId") == null ? null : java.util.UUID.fromString(String.valueOf(m.get("entityId"))),
                    String.valueOf(m.get("label")),
                    ((Number) m.get("score")).doubleValue(),
                    String.valueOf(m.get("reason")))).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Discovery: failed to deserialize cached recommendation payload.", e);
            return List.of();
        }
    }
}
