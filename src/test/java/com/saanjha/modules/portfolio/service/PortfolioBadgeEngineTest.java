package com.saanjha.modules.portfolio.service;

import com.saanjha.modules.portfolio.entity.BadgeType;
import com.saanjha.modules.portfolio.repository.PortfolioBadgeRepository;
import com.saanjha.modules.project.service.ProjectSnapshotProvider.ProjectSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioBadgeEngineTest {

    @Mock private PortfolioBadgeRepository badgeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PortfolioBadgeEngine badgeEngine;
    private UUID userId;

    @BeforeEach
    void setUp() {
        badgeEngine = new PortfolioBadgeEngine(badgeRepository, eventPublisher);
        userId = UUID.randomUUID();
    }

    @Test
    void firstLeadEntry_awardsProjectLeaderBadge() {
        when(badgeRepository.existsByUserIdAndBadgeType(userId, BadgeType.PROJECT_LEADER)).thenReturn(false);

        badgeEngine.evaluateOnEntryGenerated(userId, true, true, null, 0, 0);

        verify(badgeRepository).save(argThat(b -> b.getBadgeType() == BadgeType.PROJECT_LEADER));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void notFirstLeadEntry_doesNotReawardProjectLeaderBadge() {
        badgeEngine.evaluateOnEntryGenerated(userId, true, false, null, 0, 0);

        verify(badgeRepository, never()).save(argThat(b -> b.getBadgeType() == BadgeType.PROJECT_LEADER));
    }

    @Test
    void alreadyAwardedBadge_isNeverAwardedTwice() {
        when(badgeRepository.existsByUserIdAndBadgeType(userId, BadgeType.PROJECT_LEADER)).thenReturn(true);

        badgeEngine.evaluateOnEntryGenerated(userId, true, true, null, 0, 0);

        verify(badgeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void openSourceTag_awardsOpenSourceContributorBadge() {
        ProjectSnapshot snapshot = new ProjectSnapshot(UUID.randomUUID(), "Title", "slug", "WEB", "desc", List.of("open-source", "react"));
        when(badgeRepository.existsByUserIdAndBadgeType(userId, BadgeType.OPEN_SOURCE_CONTRIBUTOR)).thenReturn(false);

        badgeEngine.evaluateOnEntryGenerated(userId, false, false, snapshot, 0, 0);

        verify(badgeRepository).save(argThat(b -> b.getBadgeType() == BadgeType.OPEN_SOURCE_CONTRIBUTOR));
    }

    @Test
    void backendCountAtThreshold_awardsBackendSpecialistBadge() {
        when(badgeRepository.existsByUserIdAndBadgeType(userId, BadgeType.BACKEND_SPECIALIST)).thenReturn(false);

        badgeEngine.evaluateOnEntryGenerated(userId, false, false, null, 3, 0);

        verify(badgeRepository).save(argThat(b -> b.getBadgeType() == BadgeType.BACKEND_SPECIALIST));
    }

    @Test
    void backendCountBelowThreshold_doesNotAwardBadge() {
        badgeEngine.evaluateOnEntryGenerated(userId, false, false, null, 2, 0);

        verify(badgeRepository, never()).save(any());
    }

    @Test
    void milestoneValue_mapsToCorrectBadgeType() {
        when(badgeRepository.existsByUserIdAndBadgeType(eq(userId), any())).thenReturn(false);

        badgeEngine.awardMilestoneBadge(userId, 100);

        verify(badgeRepository).save(argThat(b -> b.getBadgeType() == BadgeType.TASKS_COMPLETED_100));
    }

    @Test
    void unrecognizedMilestoneValue_isSafelyIgnored() {
        badgeEngine.awardMilestoneBadge(userId, 42);

        verify(badgeRepository, never()).save(any());
    }

    @Test
    void projectHasBackendTags_isCaseInsensitive() {
        ProjectSnapshot snapshot = new ProjectSnapshot(UUID.randomUUID(), "Title", "slug", "WEB", "desc", List.of("Spring-Boot"));
        assertThat(badgeEngine.projectHasBackendTags(snapshot)).isTrue();
    }

    @Test
    void nullSnapshot_neverMatchesTagHeuristics() {
        assertThat(badgeEngine.projectHasBackendTags(null)).isFalse();
        assertThat(badgeEngine.projectHasFrontendTags(null)).isFalse();
    }
}
