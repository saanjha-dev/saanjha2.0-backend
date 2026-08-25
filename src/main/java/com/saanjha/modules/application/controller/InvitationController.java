package com.saanjha.modules.application.controller;

import com.saanjha.modules.application.dto.InvitationRequestDTOs.*;
import com.saanjha.modules.application.dto.InvitationResponseDTOs.InvitationResponse;
import com.saanjha.modules.application.service.InvitationService;
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
@Tag(name = "5. Invitations", description = "The Lead-initiated recruitment entry point")
public class InvitationController {

    private final InvitationService invitationService;

    // ========================================================================
    // SENDING (Admin, or per the team's memberInvitationPolicy — nested under the project)
    // ========================================================================

    @PostMapping("/v1/projects/{projectId}/invitations")
    @Idempotent(action = "send-invitation")
    @RateLimit(action = "send-invitation", baseLimit = 20, baseTimeSeconds = 3600)
    @PreAuthorize("hasAuthority('application:moderate') or @projectGuard.isLead(#projectId, authentication.name) or @teamGuard.canInviteToProject(#projectId, authentication.name)")
    @Operation(summary = "Send an Invitation", description = "Requires an Idempotency-Key header. Does not create Team membership directly — only on acceptance, via event. " +
            "Authorization follows the project team's configured memberInvitationPolicy (LEAD_ONLY or ANY_MEMBER) — see TeamSecurityGuard.canInviteToProject.")
    public ResponseEntity<ApiEnvelope<InvitationResponse>> send(
            @PathVariable UUID projectId, @Valid @RequestBody SendInvitationRequest request) {
        InvitationResponse response = invitationService.sendInvitation(projectId, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @GetMapping("/v1/projects/{projectId}/invitations")
    @RateLimit(action = "list-project-invitations", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @projectGuard.isLead(#projectId, authentication.name)")
    @Operation(summary = "List Invitations Sent for a Project")
    public ResponseEntity<ApiEnvelope<List<InvitationResponse>>> listForProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<InvitationResponse> result = invitationService.listInvitationsForProject(projectId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // READS (invitee)
    // ========================================================================

    @GetMapping("/v1/invitations/mine")
    @RateLimit(action = "list-my-invitations", baseLimit = 30)
    @Operation(summary = "List Invitations Addressed to Me")
    public ResponseEntity<ApiEnvelope<List<InvitationResponse>>> listMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<InvitationResponse> result = invitationService.listMyInvitations(SecurityUtils.getCurrentUserId(), buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/invitations/{id}")
    @RateLimit(action = "get-invitation", baseLimit = 30)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isInvitee(#id, authentication.name) " +
            "or @applicationGuard.isSenderOfInvitation(#id, authentication.name)")
    @Operation(summary = "Get Invitation Detail")
    public ResponseEntity<ApiEnvelope<InvitationResponse>> getInvitation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(invitationService.getInvitation(id)));
    }

    // ========================================================================
    // RESPONSES (invitee only)
    // ========================================================================

    @PatchMapping("/v1/invitations/{id}/accept")
    @RateLimit(action = "accept-invitation", baseLimit = 15)
    @PreAuthorize("@applicationGuard.isInvitee(#id, authentication.name)")
    @Operation(summary = "Accept an Invitation", description = "Publishes InvitationAcceptedEvent for the Team module to create membership.")
    public ResponseEntity<ApiEnvelope<InvitationResponse>> accept(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(invitationService.accept(id)));
    }

    @PatchMapping("/v1/invitations/{id}/decline")
    @RateLimit(action = "decline-invitation", baseLimit = 15)
    @PreAuthorize("@applicationGuard.isInvitee(#id, authentication.name)")
    @Operation(summary = "Decline an Invitation")
    public ResponseEntity<ApiEnvelope<InvitationResponse>> decline(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DeclineInvitationRequest request) {
        InvitationResponse response = invitationService.decline(id, request != null ? request : new DeclineInvitationRequest(null));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // REVOCATION (Lead/Admin only)
    // ========================================================================

    @PatchMapping("/v1/invitations/{id}/revoke")
    @RateLimit(action = "revoke-invitation", baseLimit = 15)
    @PreAuthorize("hasAuthority('application:moderate') or @applicationGuard.isSenderOfInvitation(#id, authentication.name)")
    @Operation(summary = "Revoke an Invitation")
    public ResponseEntity<ApiEnvelope<InvitationResponse>> revoke(
            @PathVariable UUID id, @Valid @RequestBody(required = false) RevokeInvitationRequest request) {
        InvitationResponse response = invitationService.revoke(
                id, SecurityUtils.getCurrentUserId(), request != null ? request : new RevokeInvitationRequest(null));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

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
