package com.saanjha.modules.discovery.recommendation;

public interface RecommendationEngine {
    RecommendationResult recommend(RecommendationRequest request);
}
