package com.saanjha.modules.discovery.controller;

import com.saanjha.modules.discovery.dto.*;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.entity.TrendingEntityType;
import com.saanjha.modules.discovery.entity.TrendingWindow;
import com.saanjha.modules.discovery.matching.MatchingEngine;
import com.saanjha.modules.discovery.recommendation.RecommendationEngine;
import com.saanjha.modules.discovery.recommendation.RecommendationRequest;
import com.saanjha.modules.discovery.search.DeveloperSearchFilters;
import com.saanjha.modules.discovery.search.ProjectSearchFilters;
import com.saanjha.modules.discovery.search.SearchService;
import com.saanjha.modules.discovery.search.SuggestionService;
import com.saanjha.modules.discovery.trending.TrendingEngine;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Discovery's public-facing surface. Search/autocomplete/trending are
 * permitAll() at the filter-chain level (see {@code SecurityConfig}) --
 * same reasoning as Project's public listing and Portfolio's public routes
 * (MES 0.3's Recruiter/Guest personas need to browse without an account).
 * Recommendations and matching require authentication (they're inherently
 * personal/ownership-scoped).
 */
@RestController
@RequestMapping("/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final SearchService searchService;
    private final SuggestionService suggestionService;
    private final TrendingEngine trendingEngine;
    private final RecommendationEngine recommendationEngine;
    private final MatchingEngine matchingEngine;

    @GetMapping("/search/projects")
    public ApiEnvelope<Page<ProjectSummaryResponse>> searchProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> requiredSkills,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false, defaultValue = "RECRUITING") String status,
            @RequestParam(required = false) Boolean hasOpenSlots,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectSearchFilters filters = new ProjectSearchFilters(
                keyword, category, requiredSkills, tags, status, hasOpenSlots);
        UUID requesterId = SecurityUtils.getCurrentUserIdOrNull();
        return ApiEnvelope.success(searchService.searchProjects(filters, PageRequest.of(page, size), requesterId));
    }

    @GetMapping("/search/developers")
    public ApiEnvelope<Page<DeveloperSummaryResponse>> searchDevelopers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) Boolean verifiedSkillsOnly,
            @RequestParam(required = false) Integer minProfileScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        DeveloperSearchFilters filters = new DeveloperSearchFilters(
                keyword, skills, experienceLevel, verifiedSkillsOnly, minProfileScore, null, null);
        UUID requesterId = SecurityUtils.getCurrentUserIdOrNull();
        return ApiEnvelope.success(searchService.searchDevelopers(filters, PageRequest.of(page, size), requesterId));
    }

    @GetMapping("/suggestions")
    public ApiEnvelope<List<SuggestionResponse>> autocomplete(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiEnvelope.success(suggestionService.autocomplete(prefix, Math.min(limit, 25)));
    }

    @GetMapping("/trending/{entityType}")
    public ApiEnvelope<List<TrendingItemResponse>> trending(
            @PathVariable TrendingEntityType entityType,
            @RequestParam(defaultValue = "DAILY") TrendingWindow window,
            @RequestParam(defaultValue = "20") int limit) {
        List<TrendingItemResponse> items = trendingEngine.getTrending(entityType, window, limit).stream()
                .map(s -> new TrendingItemResponse(s.getEntityType().name(), s.getEntityKey(), s.getScore(), s.getRank()))
                .toList();
        return ApiEnvelope.success(items);
    }

    @GetMapping("/recommendations/{type}")
    @PreAuthorize("hasAuthority('discovery:view')")
    public ApiEnvelope<List<RecommendationItemResponse>> recommendations(
            @PathVariable RecommendationType type,
            @RequestParam(required = false) UUID contextProjectId,
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        var result = recommendationEngine.recommend(
                new RecommendationRequest(userId, type, contextProjectId, Math.min(limit, 50)));
        List<RecommendationItemResponse> items = result.items().stream()
                .map(i -> new RecommendationItemResponse(i.entityId(), i.label(), i.score(), i.reason()))
                .toList();
        return ApiEnvelope.success(items);
    }

    /** Lead-only: "who should I invite?" for one specific project. */
    @GetMapping("/projects/{projectId}/matches")
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#projectId, authentication.name)")
    public ApiEnvelope<List<MatchingCandidateResponse>> matches(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "10") int limit) {
        List<MatchingCandidateResponse> candidates = matchingEngine.matchDevelopersToProject(projectId, Math.min(limit, 50))
                .stream()
                .map(c -> new MatchingCandidateResponse(c.userId(), c.displayName(), c.score().total(), c.score().breakdown()))
                .toList();
        return ApiEnvelope.success(candidates);
    }
}
