package com.saanjha.modules.discovery.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes Discovery's Micrometer instrumentation (Section
 * "Observability" of the brief: search latency, recommendation latency,
 * cache hit ratio, projection lag, ranking latency, trending rebuild
 * duration, search analytics), following the same
 * {@code MeterRegistry}/{@code Timer.builder(...)} pattern already
 * established by {@code NotificationDispatchService}.
 */
@Component
@RequiredArgsConstructor
public class DiscoveryMetrics {

    private final MeterRegistry meterRegistry;

    public void recordSearchLatency(String entityType, long millis) {
        Timer.builder("discovery.search.latency")
                .tag("entityType", entityType)
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordRecommendationLatency(String type, long millis) {
        Timer.builder("discovery.recommendation.latency")
                .tag("type", type)
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordRankingLatency(long millis) {
        Timer.builder("discovery.ranking.latency").register(meterRegistry).record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordTrendingRebuildDuration(String window, long millis) {
        Timer.builder("discovery.trending.rebuild.duration")
                .tag("window", window)
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void incrementCacheHit(String type) {
        Counter.builder("discovery.recommendation.cache.hit").tag("type", type).register(meterRegistry).increment();
    }

    public void incrementCacheMiss(String type) {
        Counter.builder("discovery.recommendation.cache.miss").tag("type", type).register(meterRegistry).increment();
    }

    public void incrementSearchQuery(String entityType, boolean hasKeyword) {
        Counter.builder("discovery.search.queries")
                .tag("entityType", entityType)
                .tag("hasKeyword", String.valueOf(hasKeyword))
                .register(meterRegistry)
                .increment();
    }

    public void recordProjectionLag(String source, long millis) {
        Timer.builder("discovery.projection.lag")
                .tag("source", source)
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }
}
