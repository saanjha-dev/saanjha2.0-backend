package com.saanjha.modules.discovery.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.discovery.entity.RecommendationCacheEntry;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.repository.RecommendationCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationEngineImplTest {

    @Mock private RecommendationCacheRepository cacheRepository;
    @Mock private RecommendationStrategy projectsStrategy;
    @Mock private DiscoveryMetrics metrics;

    private RecommendationEngineImpl engine;

    @BeforeEach
    void setUp() {
        when(projectsStrategy.supports()).thenReturn(RecommendationType.PROJECTS);
        engine = new RecommendationEngineImpl(List.of(projectsStrategy), cacheRepository, new ObjectMapper(), metrics);
    }

    @Test
    void freshRequest_callsStrategyAndWritesCache() {
        UUID userId = UUID.randomUUID();
        when(cacheRepository.findByUserIdAndRecommendationType(userId, RecommendationType.PROJECTS))
                .thenReturn(Optional.empty());
        RecommendationResult expected = new RecommendationResult(
                List.of(new RecommendationResult.Item(UUID.randomUUID(), "Some Project", 0.9, "matches skills")), false);
        when(projectsStrategy.recommend(any())).thenReturn(expected);

        RecommendationResult result = engine.recommend(new RecommendationRequest(userId, RecommendationType.PROJECTS, 10));

        assertThat(result.fromCache()).isFalse();
        assertThat(result.items()).hasSize(1);
        verify(cacheRepository).save(any(RecommendationCacheEntry.class));
    }

    @Test
    void validCacheEntry_returnsWithoutCallingStrategy() {
        UUID userId = UUID.randomUUID();
        RecommendationCacheEntry cached = new RecommendationCacheEntry();
        cached.setUserId(userId);
        cached.setRecommendationType(RecommendationType.PROJECTS);
        cached.setPayload("[]");
        cached.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cacheRepository.findByUserIdAndRecommendationType(userId, RecommendationType.PROJECTS))
                .thenReturn(Optional.of(cached));

        RecommendationResult result = engine.recommend(new RecommendationRequest(userId, RecommendationType.PROJECTS, 10));

        assertThat(result.fromCache()).isTrue();
        verifyNoInteractions(projectsStrategy);
    }

    @Test
    void contextScopedRequest_bypassesCacheEntirely() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectsStrategy.recommend(any())).thenReturn(new RecommendationResult(List.of(), false));

        engine.recommend(new RecommendationRequest(userId, RecommendationType.PROJECTS, projectId, 10));

        verifyNoInteractions(cacheRepository);
    }
}
