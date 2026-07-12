package com.saanjha.modules.discovery.dto;

import java.util.UUID;

public record RecommendationItemResponse(UUID entityId, String label, double score, String reason) {}
