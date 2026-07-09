package com.saanjha.modules.contribution.controller;

import com.saanjha.modules.contribution.dto.ContributionRequestDTOs.*;
import com.saanjha.modules.contribution.dto.ContributionResponseDTOs.*;
import com.saanjha.modules.contribution.entity.ContributionSnapshot;
import com.saanjha.modules.contribution.service.ContributionService;
import com.saanjha.shared.api.ApiEnvelope;
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
 * Read model shape follows the module's stated privacy split (see
 * {@code ContributionSecurityGuard}'s javadoc): Summary/Reputation are
 * broadly viewable (any authenticated user — this data is meant to be
 * evidence recruiters and collaborators consult); the raw ledger Timeline
 * is owner-or-moderator only.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "8. Contribution", description = "The Trust Engine: immutable ledger, reputation, and the Explanation Engine")
public class ContributionController {

    private final ContributionService contributionService;

    // ========================================================================
    // MY CONTRIBUTION (self)
    // ========================================================================

    @GetMapping("/v1/contributions/me/summary")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "My Contribution Summary")
    public ResponseEntity<ApiEnvelope<SummaryResponse>> getMySummary() {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getSummary(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/v1/contributions/me/reputation")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "My Reputation Profile")
    public ResponseEntity<ApiEnvelope<ReputationResponse>> getMyReputation() {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getReputation(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/v1/contributions/me/timeline")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "My Contribution Timeline", description = "The full explainable ledger — every entry's score breakdown.")
    public ResponseEntity<ApiEnvelope<List<LedgerEntryResponse>>> getMyTimeline(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<LedgerEntryResponse> result = contributionService.getTimeline(SecurityUtils.getCurrentUserId(), buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/contributions/me/analytics")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "My Contribution Analytics", description = "Velocity, complexity trend, review/completion ratios.")
    public ResponseEntity<ApiEnvelope<ContributionAnalyticsResponse>> getMyAnalytics() {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getAnalytics(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/v1/contributions/me/snapshots")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "My Contribution Snapshots", description = "Historical trend points, captured monthly.")
    public ResponseEntity<ApiEnvelope<List<SnapshotResponse>>> getMySnapshots(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<SnapshotResponse> result = contributionService.getSnapshots(SecurityUtils.getCurrentUserId(), buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // ANOTHER USER'S CONTRIBUTION (public-facing, per the module's privacy split)
    // ========================================================================

    @GetMapping("/v1/contributions/users/{userId}/summary")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "A User's Contribution Summary", description = "Broadly viewable by design — see the module's Portfolio-facing privacy split.")
    public ResponseEntity<ApiEnvelope<SummaryResponse>> getSummary(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getSummary(userId)));
    }

    @GetMapping("/v1/contributions/users/{userId}/reputation")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "A User's Reputation Profile")
    public ResponseEntity<ApiEnvelope<ReputationResponse>> getReputation(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getReputation(userId)));
    }

    @GetMapping("/v1/contributions/users/{userId}/timeline")
    @PreAuthorize("hasAuthority('contribution:moderate') or @contributionGuard.isOwner(#userId, authentication.name)")
    @Operation(summary = "A User's Contribution Timeline", description = "Owner-or-moderator only — the raw ledger detail is more sensitive than the polished Summary.")
    public ResponseEntity<ApiEnvelope<List<LedgerEntryResponse>>> getTimeline(
            @PathVariable UUID userId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<LedgerEntryResponse> result = contributionService.getTimeline(userId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // PROJECT / TEAM CONTRIBUTION
    // ========================================================================

    @GetMapping("/v1/projects/{projectId}/contributions")
    @PreAuthorize("hasAuthority('contribution:view')")
    @Operation(summary = "Project Contribution Breakdown", description = "Per-contributor breakdown for one project — live-aggregated over the ledger.")
    public ResponseEntity<ApiEnvelope<ProjectContributionResponse>> getProjectContribution(
            @PathVariable UUID projectId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "200") int size) {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.getProjectContribution(projectId, buildPageable(page, size))));
    }

    // ========================================================================
    // ADMIN / MODERATION
    // ========================================================================

    @PostMapping("/v1/contributions/entries/{entryId}/correct")
    @PreAuthorize("hasAuthority('contribution:moderate')")
    @Operation(summary = "Issue a Correction", description = "Never mutates history — creates a compensating reversal entry, admin-only.")
    public ResponseEntity<ApiEnvelope<LedgerEntryResponse>> correct(
            @PathVariable UUID entryId, @Valid @RequestBody CorrectionRequest request) {
        LedgerEntryResponse response = contributionService.issueCorrection(entryId, SecurityUtils.getCurrentUserId(), request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PostMapping("/v1/contributions/users/{userId}/snapshot")
    @PreAuthorize("hasAuthority('contribution:moderate')")
    @Operation(summary = "Manually Capture a Snapshot")
    public ResponseEntity<ApiEnvelope<SnapshotResponse>> manualSnapshot(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.success(contributionService.captureSnapshot(userId, ContributionSnapshot.Reason.MANUAL)));
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
