package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.UpdateSettingRequest;
import com.saanjha.modules.admin.dto.AdminResponseDTOs.PlatformSettingResponse;
import com.saanjha.modules.admin.entity.PlatformSetting;
import com.saanjha.modules.admin.service.PlatformSettingsService;
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

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "17. Admin - Platform Configuration")
@PreAuthorize("hasAuthority('admin:configure')")
public class AdminPlatformSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping
    @Operation(summary = "List Platform Settings")
    public ResponseEntity<ApiEnvelope<List<PlatformSettingResponse>>> list() {
        List<PlatformSettingResponse> settings = settingsService.listAll().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiEnvelope.success(settings));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update or Create a Platform Setting")
    public ResponseEntity<ApiEnvelope<PlatformSettingResponse>> upsert(@PathVariable String key, @Valid @RequestBody UpdateSettingRequest request) {
        PlatformSetting setting = settingsService.upsertSetting(SecurityUtils.getCurrentUserId(), key, request.settingValue(), request.valueType(), request.description());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(setting)));
    }

    @PostMapping("/maintenance-mode/enter")
    @Operation(summary = "Enter Read-only / Maintenance Mode")
    public ResponseEntity<ApiEnvelope<Void>> enterMaintenance(@RequestParam(required = false) String reason, @RequestParam(required = false) Instant estimatedEndAt) {
        settingsService.enterMaintenanceMode(SecurityUtils.getCurrentUserId(), reason, estimatedEndAt);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/maintenance-mode/exit")
    @Operation(summary = "Exit Maintenance Mode")
    public ResponseEntity<ApiEnvelope<Void>> exitMaintenance() {
        settingsService.exitMaintenanceMode(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    private PlatformSettingResponse toResponse(PlatformSetting s) {
        return new PlatformSettingResponse(s.getId(), s.getSettingKey(), s.getSettingValue(), s.getValueType().name(), s.getDescription(), s.getUpdatedAt());
    }
}
