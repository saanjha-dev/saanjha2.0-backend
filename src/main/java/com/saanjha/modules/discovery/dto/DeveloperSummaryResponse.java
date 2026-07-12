package com.saanjha.modules.discovery.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeveloperSummaryResponse(
        UUID userId,
        String displayName,
        String uniqueHandle,
        String headline,
        String location,
        String experienceLevel,
        List<Map<String, Object>> skills,
        int profileScore,
        double rankingScore,
        Map<String, Double> rankingBreakdown
) {}
