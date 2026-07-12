package com.saanjha.modules.discovery.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectSummaryResponse(
        UUID projectId,
        String title,
        String slug,
        String category,
        String visibility,
        String status,
        List<String> requiredSkills,
        List<String> tags,
        int maxTeamSize,
        int currentTeamSize,
        double rankingScore,
        Map<String, Double> rankingBreakdown
) {}
