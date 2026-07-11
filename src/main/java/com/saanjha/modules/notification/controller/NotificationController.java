package com.saanjha.modules.notification.controller;

import com.saanjha.modules.notification.dto.NotificationRequestDTOs.SetEventPreferenceRequest;
import com.saanjha.modules.notification.dto.NotificationRequestDTOs.UpdatePreferencesRequest;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.NotificationSummary;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.PreferencesResponse;
import com.saanjha.modules.notification.service.NotificationPreferenceService;
import com.saanjha.modules.notification.service.NotificationQueryService;
import com.saanjha.modules.notification.service.NotificationQueryService.ListResult;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "10. Notification", description = "The delivery sink for every user-facing event across the platform")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @PreAuthorize("hasAuthority('notification:view')")
    @RateLimit(action = "list-notifications", baseLimit = 60)
    @Operation(summary = "List My Notifications", description = "Cursor... actually offset-paginated, most recent first. Matches the master spec's GET /v1/notifications.")
    public ResponseEntity<ApiEnvelope<List<NotificationSummary>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        ListResult result = queryService.list(SecurityUtils.getCurrentUserId(), page, size, unreadOnly);
        return ResponseEntity.ok(ApiEnvelope.success(result.page().getContent(), paginationMeta(result)));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:view')")
    @Operation(summary = "Mark Notification Read")
    public ResponseEntity<ApiEnvelope<NotificationSummary>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(queryService.markRead(SecurityUtils.getCurrentUserId(), id)));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:view')")
    @Operation(summary = "Mark All Notifications Read")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> markAllRead() {
        int count = queryService.markAllRead(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(Map.of("markedRead", count)));
    }

    @GetMapping("/preferences")
    @PreAuthorize("hasAuthority('notification:view')")
    @Operation(summary = "Get My Notification Preferences")
    public ResponseEntity<ApiEnvelope<PreferencesResponse>> getPreferences() {
        return ResponseEntity.ok(ApiEnvelope.success(preferenceService.getPreferences(SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/preferences")
    @PreAuthorize("hasAuthority('notification:manage')")
    @Operation(summary = "Update My Notification Preferences", description = "Partial update - only supplied fields change.")
    public ResponseEntity<ApiEnvelope<PreferencesResponse>> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(preferenceService.updatePreferences(SecurityUtils.getCurrentUserId(), request)));
    }

    @PutMapping("/preferences/events/{eventType}")
    @PreAuthorize("hasAuthority('notification:manage')")
    @Operation(summary = "Set Per-Event Preference", description = "Mute, or move to digest mode, a specific event type (e.g. TASK_ASSIGNED) without changing global channel settings.")
    public ResponseEntity<ApiEnvelope<Void>> setEventPreference(@PathVariable String eventType, @Valid @RequestBody SetEventPreferenceRequest request) {
        preferenceService.setEventPreference(SecurityUtils.getCurrentUserId(), eventType, request.enabled(), request.mode());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    private Map<String, Object> paginationMeta(ListResult result) {
        Page<?> page = result.page();
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());
        meta.put("unreadCount", result.unreadCount());
        return meta;
    }
}
