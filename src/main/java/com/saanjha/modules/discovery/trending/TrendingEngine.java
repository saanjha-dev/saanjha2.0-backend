package com.saanjha.modules.discovery.trending;

import com.saanjha.modules.discovery.entity.TrendingEntityType;
import com.saanjha.modules.discovery.entity.TrendingSnapshot;
import com.saanjha.modules.discovery.entity.TrendingWindow;

import java.util.List;

/**
 * Computes ranked trending lists from Discovery's own read models -- never
 * at request time (Section "Trending Engine" of the brief). {@link #recompute}
 * is invoked only by {@code TrendingScheduler}; request-time reads only ever
 * call {@link #getTrending}, which is a plain indexed lookup against the
 * last computed batch.
 */
public interface TrendingEngine {
    void recompute(TrendingWindow window);
    List<TrendingSnapshot> getTrending(TrendingEntityType entityType, TrendingWindow window, int limit);
}
