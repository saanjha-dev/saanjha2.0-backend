package com.saanjha.modules.discovery.ranking;

import java.util.Map;

/** The engine's output: a total (weighted-sum) score plus a per-rule breakdown for explainability/debugging. */
public record RankingScore(double total, Map<String, Double> breakdown) {}
