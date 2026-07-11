package com.saanjha.modules.notification.controller;

import com.saanjha.modules.notification.dto.NotificationRequestDTOs.ResolveDeadLetterRequest;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.DeadLetterSummary;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.ProviderHealthSummary;
import com.saanjha.modules.notification.service.NotificationAdminService;
import com.saanjha.shared.api.ApiEnvelope;
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

/** Operational visibility/action, separate from every user-facing endpoint in {@link NotificationController} - see {@code notification:admin}'s own justification in V20's migration comment. */
@RestController
@RequestMapping("/v1/notifications/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('notification:admin')")
@Tag(name = "10. Notification", description = "Admin: dead-letter queue and provider health")
public class NotificationAdminController {

    private final NotificationAdminService adminService;

    @GetMapping("/dead-letters")
    @Operation(summary = "List Unresolved Dead Letters")
    public ResponseEntity<ApiEnvelope<List<DeadLetterSummary>>> listDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DeadLetterSummary> result = adminService.listDeadLetters(page, size);
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", result.getNumber());
        meta.put("size", result.getSize());
        meta.put("totalElements", result.getTotalElements());
        meta.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), meta));
    }

    @PostMapping("/dead-letters/{id}/resolve")
    @Operation(summary = "Resolve a Dead Letter", description = "Optionally requeues a fresh delivery attempt against the original notification.")
    public ResponseEntity<ApiEnvelope<Void>> resolveDeadLetter(@PathVariable UUID id, @Valid @RequestBody ResolveDeadLetterRequest request) {
        adminService.resolveDeadLetter(id, SecurityUtils.getCurrentUserId(), request.note(), request.requeue());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @GetMapping("/provider-health")
    @Operation(summary = "Provider Health Snapshot", description = "Persisted mirror of Resilience4j's circuit state, healthiest-first per channel.")
    public ResponseEntity<ApiEnvelope<List<ProviderHealthSummary>>> providerHealth() {
        return ResponseEntity.ok(ApiEnvelope.success(adminService.providerHealth()));
    }
}
