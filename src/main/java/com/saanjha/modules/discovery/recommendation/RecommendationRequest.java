package com.saanjha.modules.discovery.recommendation;

import com.saanjha.modules.discovery.entity.RecommendationType;

import java.util.UUID;

/**
 * {@code contextProjectId} is only meaningful for {@code TEAMMATES}
 * recommendations (a Lead asking "who should I invite to this specific
 * project?") -- every other type is scoped purely to {@code userId}.
 */
public record RecommendationRequest(UUID userId, RecommendationType type, UUID contextProjectId, int limit) {
    public RecommendationRequest(UUID userId, RecommendationType type, int limit) {
        this(userId, type, null, limit);
    }
}
