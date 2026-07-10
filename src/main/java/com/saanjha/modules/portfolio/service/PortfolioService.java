package com.saanjha.modules.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.portfolio.dto.PortfolioResponseDTOs.*;
import com.saanjha.modules.portfolio.entity.PortfolioEntry;
import com.saanjha.modules.portfolio.entity.PortfolioSummary;
import com.saanjha.modules.portfolio.entity.PortfolioVisibility;
import com.saanjha.modules.portfolio.entity.PortfolioVisibilityType;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioVisibilityChangedEvent;
import com.saanjha.modules.portfolio.repository.PortfolioBadgeRepository;
import com.saanjha.modules.portfolio.repository.PortfolioEntryRepository;
import com.saanjha.modules.portfolio.repository.PortfolioSummaryRepository;
import com.saanjha.modules.portfolio.repository.PortfolioTimelineRepository;
import com.saanjha.modules.portfolio.repository.PortfolioVisibilityRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PortfolioEntryRepository entryRepository;
    private final PortfolioSummaryRepository summaryRepository;
    private final PortfolioBadgeRepository badgeRepository;
    private final PortfolioVisibilityRepository visibilityRepository;
    private final PortfolioTimelineRepository timelineRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // PUBLIC PORTFOLIO READ (the recruiter-facing page)
    // ========================================================================

    /**
     * @param requesterId the authenticated caller, or {@code null} for an anonymous/public request
     *                    (see {@code SecurityUtils.getCurrentUserIdOrNull}). Only the owner may read
     *                    their own PRIVATE portfolio; everyone else gets NOT_FOUND — deliberately the
     *                    same error a missing user would produce, per this codebase's
     *                    "hidden by visibility rules" convention (Global Error Code Registry), so a
     *                    private portfolio's mere existence is never distinguishable from a typo'd id.
     */
    @Transactional(readOnly = true)
    public PublicPortfolioResponse getPublicPortfolio(UUID targetUserId, UUID requesterId) {
        PortfolioVisibilityType visibility = visibilityRepository.findById(targetUserId)
                .map(PortfolioVisibility::getVisibility)
                .orElse(PortfolioVisibilityType.PUBLIC); // No row yet = default (see PortfolioVisibility's Javadoc).

        boolean isOwner = requesterId != null && requesterId.equals(targetUserId);
        if (visibility == PortfolioVisibilityType.PRIVATE && !isOwner) {
            throw new AppException(ErrorCode.NOT_FOUND, "Portfolio not found.");
        }
        // LINK_ONLY: browsing straight to /v1/portfolios/{userId} without the share token is treated
        // like PRIVATE for anyone but the owner — only the dedicated /shared/{token} route honors it.
        if (visibility == PortfolioVisibilityType.LINK_ONLY && !isOwner) {
            throw new AppException(ErrorCode.NOT_FOUND, "Portfolio not found.");
        }

        return buildPublicPortfolio(targetUserId);
    }

    @Transactional(readOnly = true)
    public PublicPortfolioResponse getSharedPortfolio(String shareToken) {
        PortfolioVisibility visibility = visibilityRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Invalid or expired share link."));
        if (visibility.getVisibility() != PortfolioVisibilityType.LINK_ONLY) {
            throw new AppException(ErrorCode.NOT_FOUND, "Invalid or expired share link.");
        }
        return buildPublicPortfolio(visibility.getUserId());
    }

    private PublicPortfolioResponse buildPublicPortfolio(UUID userId) {
        PortfolioSummary summary = summaryRepository.findById(userId).orElseGet(() -> PortfolioSummary.blank(userId));
        List<PortfolioEntry> entries = entryRepository.findByUserIdOrderByCompletedAtDesc(userId);
        List<BadgeResponse> badges = badgeRepository.findByUserIdOrderByAwardedAtDesc(userId).stream()
                .map(b -> new BadgeResponse(b.getBadgeType().name(), b.getAwardedAt()))
                .toList();

        return new PublicPortfolioResponse(mapSummary(summary), entries.stream().map(this::mapEntry).toList(), badges);
    }

    // ========================================================================
    // TIMELINE
    // ========================================================================

    @Transactional(readOnly = true)
    public Page<TimelineEntryResponse> getTimeline(UUID userId, Pageable pageable) {
        return timelineRepository.findByUserIdOrderByOccurredAtDesc(userId, pageable)
                .map(e -> new TimelineEntryResponse(e.getId(), e.getProjectId(), e.getEventType().name(), e.getDescription(), e.getOccurredAt()));
    }

    // ========================================================================
    // INSIGHTS (derived on read — see the module write-up on why no dedicated table exists yet)
    // ========================================================================

    @Transactional(readOnly = true)
    public InsightsResponse getInsights(UUID userId) {
        List<PortfolioEntry> entries = entryRepository.findByUserIdOrderByCompletedAtDesc(userId);

        Map<String, Long> techCounts = entries.stream()
                .flatMap(e -> readTechnologies(e.getTechnologiesJson()).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        List<TechnologyCount> topTech = techCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new TechnologyCount(e.getKey(), e.getValue()))
                .toList();

        Map<String, Long> categoryCounts = entries.stream()
                .collect(Collectors.groupingBy(PortfolioEntry::getProjectCategory, Collectors.counting()));
        List<CategoryCount> categories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CategoryCount(e.getKey(), e.getValue()))
                .toList();

        long longestTenure = entries.stream().map(PortfolioEntry::getTenureDays).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L);
        double avgTenure = entries.stream().map(PortfolioEntry::getTenureDays).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).average().orElse(0.0);

        return new InsightsResponse(topTech, categories, longestTenure, avgTenure);
    }

    // ========================================================================
    // VISIBILITY
    // ========================================================================

    @Transactional
    public VisibilityResponse getVisibility(UUID userId) {
        PortfolioVisibility visibility = visibilityRepository.findById(userId).orElseGet(() -> PortfolioVisibility.defaultFor(userId));
        return mapVisibility(visibility);
    }

    @Transactional
    public VisibilityResponse updateVisibility(UUID userId, String visibilityName) {
        PortfolioVisibilityType type = parseVisibility(visibilityName);
        PortfolioVisibility visibility = visibilityRepository.findById(userId).orElseGet(() -> PortfolioVisibility.defaultFor(userId));
        visibility.setVisibility(type);
        if (type == PortfolioVisibilityType.LINK_ONLY) {
            // DB constraint (chk_share_token_only_when_link_only) requires a non-null token whenever
            // LINK_ONLY — reuse an existing one rather than mint a fresh one here, so a user flipping
            // PUBLIC -> LINK_ONLY -> PUBLIC -> LINK_ONLY doesn't silently break a link they'd already shared.
            if (visibility.getShareToken() == null) {
                visibility.setShareToken(generateShareToken());
            }
        } else {
            visibility.setShareToken(null); // A stale share link must stop working the moment LINK_ONLY is left.
        }
        visibility.setUpdatedAt(Instant.now());
        visibility = visibilityRepository.save(visibility);

        eventPublisher.publishEvent(new PortfolioVisibilityChangedEvent(userId, type.name(), Instant.now()));
        return mapVisibility(visibility);
    }

    @Transactional
    public VisibilityResponse issueShareLink(UUID userId) {
        PortfolioVisibility visibility = visibilityRepository.findById(userId).orElseGet(() -> PortfolioVisibility.defaultFor(userId));
        visibility.setVisibility(PortfolioVisibilityType.LINK_ONLY);
        visibility.setShareToken(generateShareToken());
        visibility.setUpdatedAt(Instant.now());
        visibility = visibilityRepository.save(visibility);

        eventPublisher.publishEvent(new PortfolioVisibilityChangedEvent(userId, PortfolioVisibilityType.LINK_ONLY.name(), Instant.now()));
        return mapVisibility(visibility);
    }

    private String generateShareToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy — not sequential, not enumerable.
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PortfolioVisibilityType parseVisibility(String raw) {
        try {
            return PortfolioVisibilityType.valueOf(raw.toUpperCase());
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown visibility value: " + raw);
        }
    }

    // ========================================================================
    // MAPPING
    // ========================================================================

    private PortfolioSummaryResponse mapSummary(PortfolioSummary summary) {
        return new PortfolioSummaryResponse(summary.getUserId(), summary.getProjectsCompleted(), summary.getLeadershipStints(),
                summary.getTotalVerifiedScore(), summary.getReliabilityScore(), summary.getLeadershipScore(),
                summary.getConsistencyScore(), summary.getReviewQualityScore(), summary.getLastGeneratedAt());
    }

    private PortfolioEntryResponse mapEntry(PortfolioEntry entry) {
        return new PortfolioEntryResponse(entry.getId(), entry.getProjectId(), entry.getProjectTitle(), entry.getProjectSlug(),
                entry.getProjectCategory(), entry.getProjectDescriptionExcerpt(), readTechnologies(entry.getTechnologiesJson()),
                entry.getRole(), entry.isWasLead(), entry.getContributionTitle(), entry.getJoinedAt(), entry.getLeftAt(),
                entry.getTenureDays(), entry.getContributionScore(), entry.getTasksCompleted(), entry.getReviewsGiven(),
                entry.getVerificationStatus().name(), entry.getCompletedAt(), entry.getGeneratedAt());
    }

    private VisibilityResponse mapVisibility(PortfolioVisibility visibility) {
        return new VisibilityResponse(visibility.getUserId(), visibility.getVisibility().name(), visibility.getShareToken());
    }

    @SuppressWarnings("unchecked")
    private List<String> readTechnologies(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
