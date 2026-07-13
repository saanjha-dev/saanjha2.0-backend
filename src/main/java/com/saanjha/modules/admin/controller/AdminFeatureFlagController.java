package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.CreateFeatureFlagRequest;
import com.saanjha.modules.admin.dto.AdminRequestDTOs.UpdateFeatureFlagRequest;
import com.saanjha.modules.admin.dto.AdminResponseDTOs.FeatureFlagResponse;
import com.saanjha.modules.admin.entity.FeatureFlag;
import com.saanjha.modules.admin.service.FeatureFlagService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/feature-flags")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "16. Admin - Feature Flags")
@PreAuthorize("hasAuthority('admin:configure')")
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    @Operation(summary = "List Feature Flags")
    public ResponseEntity<ApiEnvelope<List<FeatureFlagResponse>>> list() {
        List<FeatureFlagResponse> flags = featureFlagService.listAll().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiEnvelope.success(flags));
    }

    @PostMapping
    @Operation(summary = "Create Feature Flag")
    public ResponseEntity<ApiEnvelope<FeatureFlagResponse>> create(@Valid @RequestBody CreateFeatureFlagRequest request) {
        FeatureFlag flag = featureFlagService.createFlag(
                SecurityUtils.getCurrentUserId(), request.flagKey(), request.description(), request.flagType(),
                request.enabled(), request.rolloutPercentage(), request.targetUserIds(), request.targetProjectIds());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(flag)));
    }

    @PatchMapping("/{flagKey}")
    @Operation(summary = "Update Feature Flag (toggle, rollout %, allow-lists)")
    public ResponseEntity<ApiEnvelope<FeatureFlagResponse>> update(@PathVariable String flagKey, @RequestBody UpdateFeatureFlagRequest request) {
        FeatureFlag flag = featureFlagService.updateFlag(
                SecurityUtils.getCurrentUserId(), flagKey, request.enabled(), request.rolloutPercentage(),
                request.targetUserIds(), request.targetProjectIds());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(flag)));
    }

    @PostMapping("/{flagKey}/kill-switch")
    @Operation(summary = "Emergency Kill Switch", description = "Immediately forces the flag off, bypassing normal update friction.")
    public ResponseEntity<ApiEnvelope<FeatureFlagResponse>> killSwitch(@PathVariable String flagKey, @RequestParam(required = false) String reason) {
        FeatureFlag flag = featureFlagService.killSwitch(SecurityUtils.getCurrentUserId(), flagKey, reason);
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(flag)));
    }

    private FeatureFlagResponse toResponse(FeatureFlag f) {
        return new FeatureFlagResponse(f.getId(), f.getFlagKey(), f.getDescription(), f.getFlagType().name(), f.isEnabled(), f.getRolloutPercentage(), f.getUpdatedAt());
    }
}
