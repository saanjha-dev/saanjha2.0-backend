package com.saanjha.modules.discovery.recommendation;

import com.saanjha.modules.discovery.entity.RecommendationType;

/**
 * One pluggable recommendation algorithm per {@link RecommendationType}.
 * {@link RecommendationEngineImpl} selects the strategy whose
 * {@link #supports()} matches the request -- this is the seam a future
 * AI-scored recommender plugs into: implement this interface, register it
 * as a bean, and it replaces (or is added alongside, if multiple strategies
 * per type are ever wanted) the heuristic default without any change to the
 * engine, the controller, or the cache.
 */
public interface RecommendationStrategy {
    RecommendationType supports();
    RecommendationResult recommend(RecommendationRequest request);
}
