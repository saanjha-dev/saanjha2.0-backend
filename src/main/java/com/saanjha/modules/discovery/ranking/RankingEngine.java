package com.saanjha.modules.discovery.ranking;

/**
 * Composes every registered {@link RankingRule} into one score. Never
 * hardcodes which signals matter or by how much -- see
 * {@link RankingRuleWeights} for where weight configuration actually lives.
 */
public interface RankingEngine {
    RankingScore rank(RankingContext context);
}
