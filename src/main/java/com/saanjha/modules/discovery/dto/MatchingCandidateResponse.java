package com.saanjha.modules.discovery.dto;

import java.util.Map;
import java.util.UUID;

public record MatchingCandidateResponse(UUID userId, String displayName, double matchScore, Map<String, Double> factorBreakdown) {}
