package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.TeamModerationRequest;
import com.saanjha.modules.admin.service.TeamModerationService;
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
 * Team Moderation. A unified Admin-surface wrapper — see
 * {@code TeamModerationService}'s javadoc for why the underlying enforcement
 * already lives in the Team module's own {@code team:moderate}-gated
 * endpoints, which remain independently callable and are not superseded by
 * this controller.
 */
@RestController
@RequestMapping("/v1/admin/teams/{teamId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "14. Admin - Team Moderation")
@PreAuthorize("hasAuthority('admin:moderate')")
public class AdminTeamModerationController {

    private final TeamModerationService teamModerationService;

    @PostMapping("/lock")
    @RateLimit(action = "admin-lock-team", baseLimit = 30)
    @Operation(summary = "Lock Team")
    public ResponseEntity<ApiEnvelope<Void>> lock(@PathVariable UUID teamId, @Valid @RequestBody TeamModerationRequest request) {
        teamModerationService.lockTeam(SecurityUtils.getCurrentUserId(), teamId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/unlock")
    @RateLimit(action = "admin-unlock-team", baseLimit = 30)
    @Operation(summary = "Unlock Team")
    public ResponseEntity<ApiEnvelope<Void>> unlock(@PathVariable UUID teamId) {
        teamModerationService.unlockTeam(SecurityUtils.getCurrentUserId(), teamId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/dissolve")
    @RateLimit(action = "admin-dissolve-team", baseLimit = 15)
    @Operation(summary = "Dissolve Team", description = "No Lead can do this to their own team — Admin-only by construction.")
    public ResponseEntity<ApiEnvelope<Void>> dissolve(@PathVariable UUID teamId, @Valid @RequestBody TeamModerationRequest request) {
        teamModerationService.dissolveTeam(SecurityUtils.getCurrentUserId(), teamId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/members/{membershipId}/suspend")
    @RateLimit(action = "admin-suspend-team-member", baseLimit = 30)
    @Operation(summary = "Suspend Team Member Override")
    public ResponseEntity<ApiEnvelope<Void>> suspendMember(@PathVariable UUID teamId, @PathVariable UUID membershipId, @Valid @RequestBody TeamModerationRequest request) {
        teamModerationService.suspendMember(SecurityUtils.getCurrentUserId(), teamId, membershipId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/members/{membershipId}/reinstate")
    @RateLimit(action = "admin-reinstate-team-member", baseLimit = 30)
    @Operation(summary = "Reinstate Team Member Override")
    public ResponseEntity<ApiEnvelope<Void>> reinstateMember(@PathVariable UUID teamId, @PathVariable UUID membershipId) {
        teamModerationService.reinstateMember(SecurityUtils.getCurrentUserId(), teamId, membershipId);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }
}
