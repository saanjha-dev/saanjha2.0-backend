package com.saanjha.modules.discovery.matching;

import java.util.Map;

public record MatchingScore(double total, Map<String, Double> breakdown) {}
