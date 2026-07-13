package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminResponseDTOs.AuditLogResponse;
import com.saanjha.modules.admin.entity.AdminAuditLog;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.AdminAuditLogRepository;
import com.saanjha.shared.api.ApiEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The Audit Timeline read surface. Deliberately read-only — no endpoint here
 * or anywhere else in this module ever mutates {@link AdminAuditLog}. Gated
 * on {@code admin:audit} rather than {@code admin:moderate}: viewing the
 * technical forensic ledger (IPs, user agents, request ids) is a narrower,
 * more sensitive capability than taking moderation actions, and the two are
 * granted separately so they can be revoked separately for a moderator whose
 * access needs to be scoped down without losing their ability to act.
 */
@RestController
@RequestMapping("/v1/admin/audit-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "19. Admin - Audit")
@PreAuthorize("hasAuthority('admin:audit')")
public class AdminAuditController {

    private final AdminAuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "Global Audit Timeline")
    public ResponseEntity<ApiEnvelope<Page<AuditLogResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiEnvelope.success(auditLogRepository.findAllByOrderByOccurredAtDesc(pageable).map(this::toResponse)));
    }

    @GetMapping("/by-actor/{actorId}")
    @Operation(summary = "Audit Timeline for a specific Admin actor")
    public ResponseEntity<ApiEnvelope<Page<AuditLogResponse>>> byActor(@PathVariable UUID actorId, Pageable pageable) {
        return ResponseEntity.ok(ApiEnvelope.success(auditLogRepository.findByActorIdOrderByOccurredAtDesc(actorId, pageable).map(this::toResponse)));
    }

    @GetMapping("/by-target")
    @Operation(summary = "Audit Timeline for a specific target")
    public ResponseEntity<ApiEnvelope<Page<AuditLogResponse>>> byTarget(
            @RequestParam ModerationTargetType targetType, @RequestParam UUID targetId, Pageable pageable) {
        return ResponseEntity.ok(ApiEnvelope.success(
                auditLogRepository.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(targetType, targetId, pageable).map(this::toResponse)));
    }

    private AuditLogResponse toResponse(AdminAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorId(), log.getAction(),
                log.getTargetType() == null ? null : log.getTargetType().name(), log.getTargetId(),
                log.getReason(), log.getRequestId(), log.getOccurredAt());
    }
}
