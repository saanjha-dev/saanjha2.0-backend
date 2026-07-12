package com.saanjha.modules.discovery.recommendation;

import java.util.List;
import java.util.UUID;

public record RecommendationResult(List<Item> items, boolean fromCache) {
    public record Item(UUID entityId, String label, double score, String reason) {}
}
