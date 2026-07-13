package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.*;
import com.saanjha.modules.admin.service.UserModerationService;
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

/**
 * User Moderation. Every mutating endpoint here is {@code admin:moderate}
 * only — there is no self-service or ownership-based fallback, unlike
 * Team/Project's controllers, since moderating a user is never something the
 * target or a peer can authorize themselves into.
 */
@RestController
@RequestMapping("/v1/admin/users/{userId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "12. Admin - User Moderation")
@PreAuthorize("hasAuthority('admin:moderate')")
public class AdminUserModerationController {

    private final UserModerationService userModerationService;

    @PostMapping("/warn")
    @RateLimit(action = "admin-warn-user", baseLimit = 30)
    @Operation(summary = "Warn User")
    public ResponseEntity<ApiEnvelope<Void>> warn(@PathVariable UUID userId, @Valid @RequestBody WarnUserRequest request) {
        userModerationService.warnUser(SecurityUtils.getCurrentUserId(), userId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/suspend")
    @RateLimit(action = "admin-suspend-user", baseLimit = 30)
    @Operation(summary = "Suspend User", description = "Temporary suspension. Forces logout everywhere immediately.")
    public ResponseEntity<ApiEnvelope<Void>> suspend(@PathVariable UUID userId, @Valid @RequestBody SuspendUserRequest request) {
        userModerationService.suspendUser(SecurityUtils.getCurrentUserId(), userId, request.reason(), request.expiresAt());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unsuspend")
    @RateLimit(action = "admin-unsuspend-user", baseLimit = 30)
    @Operation(summary = "Unsuspend User")
    public ResponseEntity<ApiEnvelope<Void>> unsuspend(@PathVariable UUID userId) {
        userModerationService.unsuspendUser(SecurityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/ban")
    @RateLimit(action = "admin-ban-user", baseLimit = 30)
    @Operation(summary = "Permanently Ban User", description = "Never deletes the account — preserves history/audit integrity.")
    public ResponseEntity<ApiEnvelope<Void>> ban(@PathVariable UUID userId, @Valid @RequestBody BanUserRequest request) {
        userModerationService.banUser(SecurityUtils.getCurrentUserId(), userId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unban")
    @RateLimit(action = "admin-unban-user", baseLimit = 30)
    @Operation(summary = "Lift a Permanent Ban")
    public ResponseEntity<ApiEnvelope<Void>> unban(@PathVariable UUID userId) {
        userModerationService.unbanUser(SecurityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/shadow-ban")
    @RateLimit(action = "admin-shadow-ban-user", baseLimit = 30)
    @Operation(summary = "Shadow Ban User", description = "User is not notified; enforcement in Discovery/feeds is a future extension point.")
    public ResponseEntity<ApiEnvelope<Void>> shadowBan(@PathVariable UUID userId, @Valid @RequestBody BanUserRequest request) {
        userModerationService.shadowBanUser(SecurityUtils.getCurrentUserId(), userId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/shadow-ban/lift")
    @RateLimit(action = "admin-lift-shadow-ban", baseLimit = 30)
    @Operation(summary = "Lift Shadow Ban")
    public ResponseEntity<ApiEnvelope<Void>> liftShadowBan(@PathVariable UUID userId) {
        userModerationService.liftShadowBan(SecurityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/roles/grant")
    @RateLimit(action = "admin-grant-role", baseLimit = 20)
    @Operation(summary = "Role Escalation", description = "Cannot be used by an administrator to grant themselves a role.")
    public ResponseEntity<ApiEnvelope<Void>> grantRole(@PathVariable UUID userId, @Valid @RequestBody RoleChangeRequest request) {
        userModerationService.grantRole(SecurityUtils.getCurrentUserId(), userId, request.roleName(), request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/roles/revoke")
    @RateLimit(action = "admin-revoke-role", baseLimit = 20)
    @Operation(summary = "Role Removal", description = "Cannot be used by an administrator to revoke their own admin role.")
    public ResponseEntity<ApiEnvelope<Void>> revokeRole(@PathVariable UUID userId, @Valid @RequestBody RoleChangeRequest request) {
        userModerationService.revokeRole(SecurityUtils.getCurrentUserId(), userId, request.roleName(), request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }
}
