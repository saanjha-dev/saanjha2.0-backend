package com.saanjha.modules.portfolio.controller;

import com.saanjha.modules.portfolio.dto.PortfolioRequestDTOs.*;
import com.saanjha.modules.portfolio.dto.PortfolioResponseDTOs.*;
import com.saanjha.modules.portfolio.service.PortfolioService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public-read routes ({@code /{userId}}, {@code /shared/{token}},
 * {@code /{userId}/timeline}) are {@code permitAll()} in SecurityConfig —
 * the recruiter persona this module exists for is, by definition, often
 * anonymous. Every one of those calls {@code SecurityUtils.getCurrentUserIdOrNull()},
 * never the strict variant, following the exact fix already applied to
 * {@code UserController.getPublicProfile} for the same reason.
 */
@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "9. Portfolio", description = "The Reputation Engine: verified, evidence-derived proof of work")
public class PortfolioController {

    private final PortfolioService portfolioService;

    // ========================================================================
    // MY PORTFOLIO
    // ========================================================================

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('portfolio:view')")
    @Operation(summary = "Get My Portfolio")
    public ResponseEntity<ApiEnvelope<PublicPortfolioResponse>> getMyPortfolio() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.getPublicPortfolio(userId, userId)));
    }

    @PatchMapping("/me/visibility")
    @PreAuthorize("hasAuthority('portfolio:manage')")
    @Operation(summary = "Update Portfolio Visibility", description = "Set to PUBLIC, PRIVATE, or LINK_ONLY. Leaving LINK_ONLY invalidates any existing share link.")
    public ResponseEntity<ApiEnvelope<VisibilityResponse>> updateVisibility(@Valid @RequestBody UpdateVisibilityRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.updateVisibility(SecurityUtils.getCurrentUserId(), request.visibility())));
    }

    @PostMapping("/me/share")
    @PreAuthorize("hasAuthority('portfolio:manage')")
    @Operation(summary = "Share Portfolio", description = "Switches visibility to LINK_ONLY and issues a fresh, high-entropy share token.")
    public ResponseEntity<ApiEnvelope<VisibilityResponse>> sharePortfolio() {
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.issueShareLink(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/me/insights")
    @PreAuthorize("hasAuthority('portfolio:view')")
    @Operation(summary = "My Portfolio Insights", description = "Most-used technologies, project category distribution, and tenure statistics.")
    public ResponseEntity<ApiEnvelope<InsightsResponse>> getMyInsights() {
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.getInsights(SecurityUtils.getCurrentUserId())));
    }

    // ========================================================================
    // PUBLIC / RECRUITER VIEW
    // ========================================================================

    @GetMapping("/{userId}")
    @RateLimit(action = "get-public-portfolio", baseLimit = 30)
    @Operation(summary = "Get Public Portfolio", description = "Viewable anonymously, per this route's permitAll() SecurityConfig entry. Enforces the owner's visibility setting: PRIVATE and LINK_ONLY both return 404 to any non-owner here.")
    public ResponseEntity<ApiEnvelope<PublicPortfolioResponse>> getPublicPortfolio(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.getPublicPortfolio(userId, SecurityUtils.getCurrentUserIdOrNull())));
    }

    @GetMapping("/shared/{token}")
    @RateLimit(action = "get-shared-portfolio", baseLimit = 30)
    @Operation(summary = "Get Portfolio via Share Link", description = "The one route that honors a LINK_ONLY share token. Viewable anonymously.")
    public ResponseEntity<ApiEnvelope<PublicPortfolioResponse>> getSharedPortfolio(@PathVariable String token) {
        return ResponseEntity.ok(ApiEnvelope.success(portfolioService.getSharedPortfolio(token)));
    }

    @GetMapping("/{userId}/timeline")
    @PreAuthorize("hasAuthority('portfolio:view')")
    @RateLimit(action = "get-portfolio-timeline", baseLimit = 30)
    @Operation(summary = "Portfolio Timeline", description = "Chronological, event-driven activity feed. Same visibility rule as the public portfolio itself is enforced by the summary/entries read; the timeline route intentionally does not leak PRIVATE users' activity, so it is restricted to the caller's own timeline only.")
    public ResponseEntity<ApiEnvelope<List<TimelineEntryResponse>>> getTimeline(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID requesterId = SecurityUtils.getCurrentUserId();
        if (!requesterId.equals(userId)) {
            // FIX-BY-DESIGN: unlike the summary/entries view, the raw timeline has no per-row
            // visibility gate of its own — restricting it to the owner avoids silently building a
            // second, inconsistent privacy surface. Widening this to "anyone who can see the public
            // portfolio" is a reasonable future extension, not implemented v1 (see module write-up).
            throw new AppException(ErrorCode.FORBIDDEN, "You may only view your own timeline.");
        }
        Page<TimelineEntryResponse> result = portfolioService.getTimeline(userId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Pageable buildPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    private Map<String, Object> paginationMeta(Page<?> page) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());
        return meta;
    }
}
