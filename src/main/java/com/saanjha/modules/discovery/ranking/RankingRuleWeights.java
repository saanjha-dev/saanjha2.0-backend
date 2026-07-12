package com.saanjha.modules.discovery.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Externalized weight per {@link RankingRule#name()}, bound from
 * {@code discovery.ranking.weights.*} in application.yml. A rule whose name
 * has no configured entry falls back to its own
 * {@code RankingRule}-implementation default (each rule constructor takes a
 * fallback weight) -- so the system is fully functional out of the box and
 * tunable in production without a redeploy.
 */
@Component
@ConfigurationProperties(prefix = "discovery.ranking")
public class RankingRuleWeights {

    private Map<String, Double> weights = new HashMap<>();

    public Map<String, Double> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Double> weights) {
        this.weights = weights;
    }

    public double weightFor(String ruleName, double fallback) {
        return weights.getOrDefault(ruleName, fallback);
    }
}
