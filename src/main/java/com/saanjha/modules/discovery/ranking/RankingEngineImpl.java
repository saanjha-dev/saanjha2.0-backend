package com.saanjha.modules.discovery.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted-sum composition of every registered {@link RankingRule}. Each
 * rule already returns a normalized [0, 1] score; this class only applies
 * weights and sums -- all domain scoring logic lives in the rules
 * themselves, never here, so a new signal never requires touching this
 * class.
 */
@Service
@RequiredArgsConstructor
public class RankingEngineImpl implements RankingEngine {

    private final List<RankingRule> rules;
    private final RankingRuleWeights weights;

    @Override
    public RankingScore rank(RankingContext context) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double total = 0;
        double totalWeight = 0;

        for (RankingRule rule : rules) {
            double weight = weights.weightFor(rule.name(), 1.0);
            double score = clamp(rule.score(context));
            breakdown.put(rule.name(), score);
            total += score * weight;
            totalWeight += weight;
        }

        double normalizedTotal = totalWeight > 0 ? total / totalWeight : 0;
        return new RankingScore(normalizedTotal, breakdown);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
