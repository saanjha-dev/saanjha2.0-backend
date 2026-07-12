package com.saanjha.modules.discovery.ranking;

/**
 * One independent, pluggable ranking signal. Implementations are Spring
 * beans, auto-discovered by {@link RankingEngineImpl} (which injects
 * {@code List<RankingRule>}) -- adding a new signal means adding a new bean,
 * never editing the engine. This is the seam a future AI-scored ranking
 * signal plugs into without touching any existing rule.
 */
public interface RankingRule {

    /** Stable identifier used as the breakdown map key and the weights config key. */
    String name();

    /**
     * Returns a score in [0, 1] for the given context. Implementations must
     * return 0 (not throw, not return null) when their signal doesn't apply
     * to this entity type or is unknown/not yet computed.
     */
    double score(RankingContext context);
}
