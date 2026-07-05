package com.saanjha.modules.application.controller;

import com.saanjha.modules.application.dto.ApplicationRequestDTOs.*;
import com.saanjha.modules.application.dto.ApplicationResponseDTOs.*;
import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.service.ApplicationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.idempotency.Idempotent;
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

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Applications", description = "The recruitment workflow: submit, review, and decide on project applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    // ========================================================================
    // SUBMISSION (nested under the project being applied to)
    // ========================================================================

    @PostMapping("/v1/projects/{projectId}/applications")
    @Idempotent(action = "submit-application")
    @RateLimit(action = "submit-application", baseLimit = 10, baseTimeSeconds = 3600)
    @PreAuthorize("hasAuthority('application:submit')")
    @Operation(summary = "Submit an Application", description = "Applies to join a RECRUITING, public project. Requires an Idempotency-Key header.")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> submit(
            @PathVariable UUID projectId, @Valid @RequestBody SubmitApplicationRequest request) {
        ApplicationResponse response = applicationService.submitApplication(SecurityUtils.getCurrentUserId(), projectId, request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @GetMapping("/v1/projects/{projectId}/applications")
    @RateLimit(action = "list-project-applications", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @projectGuard.isLead(#projectId, authentication.name)")
    @Operation(summary = "List Applications for a Project", description = "Owner Dashboard: paginated, optionally filtered by status.")
    public ResponseEntity<ApiEnvelope<List<ApplicationSummaryResponse>>> listForProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ApplicationSummaryResponse> result = applicationService.listApplicationsForProject(projectId, status, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/projects/{projectId}/applications/stats")
    @RateLimit(action = "get-application-stats", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @projectGuard.isLead(#projectId, authentication.name)")
    @Operation(summary = "Application Statistics", description = "Counts of applications by status for the Owner Dashboard.")
    public ResponseEntity<ApiEnvelope<ApplicationStatsResponse>> getStats(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiEnvelope.success(applicationService.getStats(projectId)));
    }

    @PostMapping("/v1/projects/{projectId}/applications/bulk-review")
    @RateLimit(action = "bulk-review-applications", baseLimit = 10)
    @PreAuthorize("hasAuthority('application:moderate') or @projectGuard.isLead(#projectId, authentication.name)")
    @Operation(summary = "Bulk Review", description = "Applies SHORTLIST/ACCEPT/REJECT to multiple applications at once. Per-item failures don't abort the batch.")
    public ResponseEntity<ApiEnvelope<BulkReviewResultResponse>> bulkReview(
            @PathVariable UUID projectId, @Valid @RequestBody BulkReviewRequest request) {
        BulkReviewResultResponse response = applicationService.bulkReview(projectId, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // INDIVIDUAL APPLICATION READS
    // ========================================================================

    @GetMapping("/v1/applications/mine")
    @RateLimit(action = "list-my-applications", baseLimit = 30)
    @Operation(summary = "List My Applications")
    public ResponseEntity<ApiEnvelope<List<ApplicationSummaryResponse>>> listMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<ApplicationSummaryResponse> result = applicationService.listMyApplications(SecurityUtils.getCurrentUserId(), buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/applications/{id}")
    @RateLimit(action = "get-application", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isApplicant(#id, authentication.name) " +
            "or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Get Application Detail")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(applicationService.getApplication(id)));
    }

    @GetMapping("/v1/applications/{id}/timeline")
    @RateLimit(action = "get-application-timeline", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isApplicant(#id, authentication.name) " +
            "or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Get Application Timeline", description = "The append-only ledger of every status transition.")
    public ResponseEntity<ApiEnvelope<List<ApplicationStatusLogResponse>>> getTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(applicationService.getTimeline(id)));
    }

    // ========================================================================
    // WITHDRAWAL (applicant only)
    // ========================================================================

    @PatchMapping("/v1/applications/{id}/withdraw")
    @RateLimit(action = "withdraw-application", baseLimit = 15)
    @PreAuthorize("@applicationGuard.isApplicant(#id, authentication.name)")
    @Operation(summary = "Withdraw an Application")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> withdraw(@PathVariable UUID id) {
        ApplicationResponse response = applicationService.withdraw(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // REVIEW ACTIONS (Lead/Admin only)
    // ========================================================================

    @PatchMapping("/v1/applications/{id}/review")
    @RateLimit(action = "review-application", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Mark Under Review")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> markUnderReview(@PathVariable UUID id) {
        ApplicationResponse response = applicationService.markUnderReview(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/applications/{id}/shortlist")
    @RateLimit(action = "shortlist-application", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Shortlist an Application")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> shortlist(@PathVariable UUID id) {
        ApplicationResponse response = applicationService.shortlist(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/applications/{id}/accept")
    @RateLimit(action = "accept-application", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Accept an Application", description = "Publishes ApplicationAcceptedEvent for the Team module to create membership.")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> accept(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        ApplicationResponse response = applicationService.accept(id, SecurityUtils.getCurrentUserId(), orEmpty(request));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/applications/{id}/reject")
    @RateLimit(action = "reject-application", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Reject an Application")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        ApplicationResponse response = applicationService.reject(id, SecurityUtils.getCurrentUserId(), orEmpty(request));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/applications/{id}/reopen")
    @RateLimit(action = "reopen-application", baseLimit = 15)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Reopen a Rejected Application", description = "The one documented exception to terminal REJECTED: moves it back to UNDER_REVIEW.")
    public ResponseEntity<ApiEnvelope<ApplicationResponse>> reopen(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        ApplicationResponse response = applicationService.reopen(id, SecurityUtils.getCurrentUserId(), orEmpty(request));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // INTERNAL NOTES (Lead/Admin only)
    // ========================================================================

    @PostMapping("/v1/applications/{id}/notes")
    @RateLimit(action = "add-application-note", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "Add an Internal Note", description = "Never visible to the applicant.")
    public ResponseEntity<ApiEnvelope<ApplicationNoteResponse>> addNote(
            @PathVariable UUID id, @Valid @RequestBody AddNoteRequest request) {
        ApplicationNoteResponse response = applicationService.addNote(id, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @GetMapping("/v1/applications/{id}/notes")
    @RateLimit(action = "get-application-notes", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isReviewerOfApplication(#id, authentication.name)")
    @Operation(summary = "List Internal Notes")
    public ResponseEntity<ApiEnvelope<List<ApplicationNoteResponse>>> getNotes(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(applicationService.getNotes(id)));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private ReviewDecisionRequest orEmpty(ReviewDecisionRequest request) {
        return request != null ? request : new ReviewDecisionRequest(null);
    }

    private Pageable buildPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
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
