package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.*;
import com.saanjha.modules.admin.dto.AdminResponseDTOs.ReportResponse;
import com.saanjha.modules.admin.entity.Report;
import com.saanjha.modules.admin.entity.ReportStatus;
import com.saanjha.modules.admin.service.ContentModerationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Content Moderation / Reports & Appeals. {@code POST /v1/reports} is
 * deliberately open to any authenticated user (permission {@code
 * report:submit}, granted to ROLE_USER and ROLE_ADMIN) — reporting content is
 * a platform-wide safety feature, not an admin capability. Everything under
 * {@code /v1/admin/reports} and {@code /v1/admin/appeals} is moderator-only.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "15. Admin - Reports & Appeals")
public class AdminReportController {

    private final ContentModerationService contentModerationService;

    @PostMapping("/v1/reports")
    @PreAuthorize("hasAuthority('report:submit')")
    @RateLimit(action = "submit-report", baseLimit = 20)
    @Operation(summary = "Report Content/User/Project/Message/Portfolio")
    public ResponseEntity<ApiEnvelope<ReportResponse>> submit(@Valid @RequestBody SubmitReportRequest request) {
        Report report = contentModerationService.submitReport(
                SecurityUtils.getCurrentUserId(), request.targetType(), request.targetId(), request.category(), request.description());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(report)));
    }

    @GetMapping("/v1/admin/reports")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @Operation(summary = "Review Queue")
    public ResponseEntity<ApiEnvelope<Page<ReportResponse>>> reviewQueue(
            @RequestParam(required = false, defaultValue = "OPEN,IN_REVIEW,ESCALATED") List<ReportStatus> statuses,
            Pageable pageable) {
        Page<ReportResponse> page = contentModerationService.getReviewQueue(statuses, pageable).map(this::toResponse);
        return ResponseEntity.ok(ApiEnvelope.success(page));
    }

    @GetMapping("/v1/admin/reports/my-queue")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @Operation(summary = "My Assigned Queue")
    public ResponseEntity<ApiEnvelope<Page<ReportResponse>>> myQueue(Pageable pageable) {
        Page<ReportResponse> page = contentModerationService.getModeratorQueue(SecurityUtils.getCurrentUserId(), pageable).map(this::toResponse);
        return ResponseEntity.ok(ApiEnvelope.success(page));
    }

    @PostMapping("/v1/admin/reports/{reportId}/assign")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @Operation(summary = "Assign Report to Moderator")
    public ResponseEntity<ApiEnvelope<ReportResponse>> assign(@PathVariable UUID reportId, @Valid @RequestBody AssignReportRequest request) {
        Report report = contentModerationService.assignReport(SecurityUtils.getCurrentUserId(), reportId, request.moderatorId());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(report)));
    }

    @PostMapping("/v1/admin/reports/{reportId}/resolve")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @RateLimit(action = "admin-resolve-report", baseLimit = 60)
    @Operation(summary = "Decision Queue: Resolve or Dismiss a Report")
    public ResponseEntity<ApiEnvelope<ReportResponse>> resolve(@PathVariable UUID reportId, @Valid @RequestBody ResolveReportRequest request) {
        Report report = contentModerationService.resolveReport(SecurityUtils.getCurrentUserId(), reportId, request.resolution(), request.resolutionNotes());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(report)));
    }

    // ---- Appeals ----

    @PostMapping("/v1/appeals")
    @PreAuthorize("hasAuthority('report:submit')")
    @RateLimit(action = "submit-appeal", baseLimit = 10)
    @Operation(summary = "Appeal a Moderation Action taken against you")
    public ResponseEntity<ApiEnvelope<Void>> submitAppeal(@Valid @RequestBody SubmitAppealRequest request) {
        contentModerationService.submitAppeal(SecurityUtils.getCurrentUserId(), request.moderationActionId(), request.statement());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @GetMapping("/v1/admin/appeals")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @Operation(summary = "Appeal Queue")
    public ResponseEntity<ApiEnvelope<?>> appealQueue(Pageable pageable) {
        return ResponseEntity.ok(ApiEnvelope.success(contentModerationService.getAppealQueue(pageable)));
    }

    @PostMapping("/v1/admin/appeals/{appealId}/decide")
    @PreAuthorize("hasAuthority('admin:moderate')")
    @RateLimit(action = "admin-decide-appeal", baseLimit = 30)
    @Operation(summary = "Decide an Appeal (grant reverses the underlying moderation action)")
    public ResponseEntity<ApiEnvelope<Void>> decideAppeal(@PathVariable UUID appealId, @Valid @RequestBody DecideAppealRequest request) {
        contentModerationService.decideAppeal(SecurityUtils.getCurrentUserId(), appealId, request.grant(), request.decisionNotes());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(r.getId(), r.getReporterUserId(), r.getTargetType().name(), r.getTargetId(),
                r.getCategory().name(), r.getDescription(), r.getStatus().name(), r.getAssignedModeratorId(),
                r.getResolutionNotes(), r.getResolvedBy(), r.getResolvedAt(), r.getCreatedAt());
    }
}
