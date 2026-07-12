package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.RecommendationCacheEntry;
import com.saanjha.modules.discovery.entity.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationCacheRepository extends JpaRepository<RecommendationCacheEntry, UUID> {
    Optional<RecommendationCacheEntry> findByUserIdAndRecommendationType(UUID userId, RecommendationType type);
}
