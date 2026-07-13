package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.ProjectModerationRequest;
import com.saanjha.modules.admin.service.ProjectModerationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/projects/{projectId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "13. Admin - Project Moderation")
@PreAuthorize("hasAuthority('admin:moderate')")
public class AdminProjectModerationController {

    private final ProjectModerationService projectModerationService;

    @PostMapping("/lock")
    @RateLimit(action = "admin-lock-project", baseLimit = 30)
    @Operation(summary = "Lock Project", description = "Governance overlay flag distinct from Project's own state machine.")
    public ResponseEntity<ApiEnvelope<Void>> lock(@PathVariable UUID projectId, @Valid @RequestBody ProjectModerationRequest request) {
        projectModerationService.lockProject(SecurityUtils.getCurrentUserId(), projectId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unlock")
    @RateLimit(action = "admin-unlock-project", baseLimit = 30)
    @Operation(summary = "Unlock Project")
    public ResponseEntity<ApiEnvelope<Void>> unlock(@PathVariable UUID projectId) {
        projectModerationService.unlockProject(SecurityUtils.getCurrentUserId(), projectId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/hide")
    @RateLimit(action = "admin-hide-project", baseLimit = 30)
    @Operation(summary = "Hide Project", description = "Removes from public Discovery surfaces without archiving.")
    public ResponseEntity<ApiEnvelope<Void>> hide(@PathVariable UUID projectId, @Valid @RequestBody ProjectModerationRequest request) {
        projectModerationService.hideProject(SecurityUtils.getCurrentUserId(), projectId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unhide")
    @RateLimit(action = "admin-unhide-project", baseLimit = 30)
    @Operation(summary = "Unhide Project")
    public ResponseEntity<ApiEnvelope<Void>> unhide(@PathVariable UUID projectId) {
        projectModerationService.unhideProject(SecurityUtils.getCurrentUserId(), projectId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/feature")
    @RateLimit(action = "admin-feature-project", baseLimit = 30)
    @Operation(summary = "Feature Project")
    public ResponseEntity<ApiEnvelope<Void>> feature(@PathVariable UUID projectId) {
        projectModerationService.featureProject(SecurityUtils.getCurrentUserId(), projectId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unfeature")
    @RateLimit(action = "admin-unfeature-project", baseLimit = 30)
    @Operation(summary = "Unfeature Project")
    public ResponseEntity<ApiEnvelope<Void>> unfeature(@PathVariable UUID projectId) {
        projectModerationService.unfeatureProject(SecurityUtils.getCurrentUserId(), projectId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/remove")
    @RateLimit(action = "admin-remove-project", baseLimit = 15)
    @Operation(summary = "Remove Project", description = "Transitions the project to ARCHIVED via Project's own, already-validated state machine.")
    public ResponseEntity<ApiEnvelope<Void>> remove(@PathVariable UUID projectId, @Valid @RequestBody ProjectModerationRequest request) {
        projectModerationService.removeProject(SecurityUtils.getCurrentUserId(), projectId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }
}
