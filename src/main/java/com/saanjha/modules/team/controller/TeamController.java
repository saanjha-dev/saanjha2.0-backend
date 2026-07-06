package com.saanjha.modules.team.controller;

import com.saanjha.modules.team.dto.TeamRequestDTOs.*;
import com.saanjha.modules.team.dto.TeamResponseDTOs.*;
import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.modules.team.service.TeamService;
import com.saanjha.shared.api.ApiEnvelope;
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

/**
 * FIX (TD18/S12, architecture-review.md §9.3): every read endpoint here
 * previously authorized on the blanket {@code team:participate} permission —
 * held by every ROLE_USER, unconditionally — with no team-membership check
 * at all, despite {@code TeamSecurityGuard.isMember(...)} already existing,
 * unit-tested, and simply never called. This was a live, exploitable-today
 * broken-object-level-authorization gap: any authenticated user could view
 * any team's roster, history, and metrics by guessing/enumerating a UUID.
 *
 * The fix is deliberately NOT a blanket "clamp everything to isMember()" —
 * that would leave {@code TeamSettings.visibility} permanently decorative,
 * which the same review section separately flagged. Two tiers instead:
 *  - Roster-level reads (team detail, roster view, member list) use
 *    {@code isVisibleTo}: visible to live members OR to any authenticated
 *    user if the team's own visibility setting is PUBLIC.
 *  - Sensitive reads (history, per-member history, metrics) use
 *    {@code isMember} only — no visibility exception — since these expose
 *    free-text removal reasons and operational detail a PUBLIC roster
 *    setting was never meant to make world-readable.
 *  - {@code GET /v1/teams/{id}/me} deliberately keeps the blanket
 *    {@code team:participate} check: it only ever returns the CALLER's own
 *    membership (resolved from the security context, not a path parameter),
 *    so there is no other-user's-data exposure to gate in the first place.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "6. Teams", description = "Collaboration membership: roster, leadership, and history")
public class TeamController {

    private final TeamService teamService;

    // ========================================================================
    // LOOKUPS
    // ========================================================================

    @GetMapping("/v1/projects/{projectId}/team")
    @RateLimit(action = "get-team-by-project", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isProjectsTeamVisibleTo(#projectId, authentication.name)")
    @Operation(summary = "Get Team by Project", description = "Convenience lookup for callers that only have a projectId on hand.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> getTeamByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getTeamByProject(projectId)));
    }

    @GetMapping("/v1/teams/{id}")
    @RateLimit(action = "get-team", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isVisibleTo(#id, authentication.name)")
    @Operation(summary = "Get Team")
    public ResponseEntity<ApiEnvelope<TeamResponse>> getTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getTeam(id)));
    }

    @GetMapping("/v1/teams/{id}/roster-view")
    @RateLimit(action = "get-roster-view", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isVisibleTo(#id, authentication.name)")
    @Operation(summary = "Dashboard Roster View", description = "Team + leader + members in one payload, so the frontend doesn't assemble several calls.")
    public ResponseEntity<ApiEnvelope<RosterViewResponse>> getRosterView(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getRosterView(id)));
    }

    @GetMapping("/v1/teams/{id}/members")
    @RateLimit(action = "get-team-members", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isVisibleTo(#id, authentication.name)")
    @Operation(summary = "Display Active Organizational Structure", description = "Paginated roster; defaults to ACTIVE+SUSPENDED, filterable by exact status.")
    public ResponseEntity<ApiEnvelope<List<MembershipSummaryResponse>>> getMembers(
            @PathVariable UUID id,
            @RequestParam(required = false) MembershipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MembershipSummaryResponse> result = teamService.getRoster(id, status, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/teams/{id}/me")
    @RateLimit(action = "get-my-membership", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:participate')")
    @Operation(summary = "Get Current User's Membership")
    public ResponseEntity<ApiEnvelope<CurrentUserMembershipResponse>> getMyMembership(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getCurrentUserMembership(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/v1/teams/{id}/history")
    @RateLimit(action = "get-team-history", baseLimit = 20)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Get Team History", description = "The append-only ledger: who joined, left, was removed, or transferred leadership, and when. Membership required — this is deliberately NOT covered by the PUBLIC-visibility exception, since it includes free-text removal reasons.")
    public ResponseEntity<ApiEnvelope<List<MembershipHistoryResponse>>> getHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<MembershipHistoryResponse> result = teamService.getHistory(id, buildHistoryPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/teams/{id}/members/{membershipId}/history")
    @RateLimit(action = "get-member-history", baseLimit = 20)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Get One Member's History")
    public ResponseEntity<ApiEnvelope<List<MembershipHistoryResponse>>> getMemberHistory(
            @PathVariable UUID id, @PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getMembershipHistory(membershipId)));
    }

    @GetMapping("/v1/teams/{id}/metrics")
    @RateLimit(action = "get-team-metrics", baseLimit = 30)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Get Team Metrics", description = "Cheap, incrementally-maintained counters — never recomputed from history on read. Membership required, not covered by the visibility exception.")
    public ResponseEntity<ApiEnvelope<TeamMetricsResponse>> getMetrics(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.getMetrics(id)));
    }

    // ========================================================================
    // LEADERSHIP TRANSFER (current Lead only)
    // ========================================================================

    @PatchMapping("/v1/teams/{id}/leadership")
    @RateLimit(action = "transfer-leadership", baseLimit = 10)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Transfer Leadership", description = "Only the current Lead (or an Admin) may initiate this.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> transferLeadership(
            @PathVariable UUID id, @Valid @RequestBody TransferLeadershipRequest request) {
        TeamResponse response = teamService.transferLeadership(id, SecurityUtils.getCurrentUserId(), request.newLeadUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // REMOVAL / LEAVING / SUSPENSION
    // ========================================================================

    @DeleteMapping("/v1/teams/{id}/members/{uId}")
    @RateLimit(action = "remove-member", baseLimit = 15)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Remove an Inactive Collaborator", description = "Matches the platform's documented endpoint registry: {uId} is a user id, not a membership id.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> removeMember(
            @PathVariable UUID id, @PathVariable UUID uId, @Valid @RequestBody(required = false) RemoveMemberRequest request) {
        String reason = request != null ? request.reason() : null;
        TeamResponse response = teamService.removeMemberByUserId(id, SecurityUtils.getCurrentUserId(), uId, reason);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/teams/{id}/leave")
    @RateLimit(action = "leave-team", baseLimit = 10)
    @PreAuthorize("hasAuthority('team:participate')")
    @Operation(summary = "Leave Team", description = "Self-service. The sole-member Lead cannot leave — see the response's error message for the documented alternative.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> leaveTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.leaveTeam(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/v1/teams/{id}/members/{membershipId}/suspend")
    @RateLimit(action = "suspend-member", baseLimit = 15)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Suspend a Member", description = "Keeps their roster seat but blocks action, pending review.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> suspendMember(
            @PathVariable UUID id, @PathVariable UUID membershipId, @Valid @RequestBody SuspendMemberRequest request) {
        TeamResponse response = teamService.suspendMember(id, SecurityUtils.getCurrentUserId(), membershipId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/teams/{id}/members/{membershipId}/reinstate")
    @RateLimit(action = "reinstate-member", baseLimit = 15)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Reinstate a Suspended Member")
    public ResponseEntity<ApiEnvelope<TeamResponse>> reinstateMember(@PathVariable UUID id, @PathVariable UUID membershipId) {
        TeamResponse response = teamService.reinstateMember(id, SecurityUtils.getCurrentUserId(), membershipId);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // TEAM-LEVEL LIFECYCLE
    // ========================================================================

    @PatchMapping("/v1/teams/{id}/lock")
    @RateLimit(action = "lock-team", baseLimit = 10)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Lock Team", description = "Reversible freeze — no roster mutations while locked.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> lockTeam(
            @PathVariable UUID id, @Valid @RequestBody(required = false) LockTeamRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(ApiEnvelope.success(teamService.lockTeam(id, SecurityUtils.getCurrentUserId(), reason)));
    }

    @PatchMapping("/v1/teams/{id}/unlock")
    @RateLimit(action = "unlock-team", baseLimit = 10)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Unlock Team")
    public ResponseEntity<ApiEnvelope<TeamResponse>> unlockTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.unlockTeam(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/v1/teams/{id}/dissolve")
    @RateLimit(action = "dissolve-team", baseLimit = 5)
    @PreAuthorize("hasAuthority('team:moderate')")
    @Operation(summary = "Dissolve Team", description = "Admin-only. No Lead can dissolve their own team — see the approved architecture spec, Section 10.")
    public ResponseEntity<ApiEnvelope<TeamResponse>> dissolveTeam(
            @PathVariable UUID id, @Valid @RequestBody DissolveTeamRequest request) {
        TeamResponse response = teamService.dissolveTeam(id, SecurityUtils.getCurrentUserId(), request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // SETTINGS
    // ========================================================================

    @PatchMapping("/v1/teams/{id}/settings")
    @RateLimit(action = "update-team-settings", baseLimit = 15)
    @PreAuthorize("hasAuthority('team:moderate') or @teamGuard.isLeadOfTeam(#id, authentication.name)")
    @Operation(summary = "Update Team Settings")
    public ResponseEntity<ApiEnvelope<TeamResponse>> updateSettings(
            @PathVariable UUID id, @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(teamService.updateSettings(id, request)));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Pageable buildPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "joinedAt"));
    }

    private Pageable buildHistoryPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        // No Sort here: the repository method's own OrderByOccurredAtDesc already
        // fixes ordering, and MembershipHistory has no "joinedAt" field to conflict with.
        return PageRequest.of(safePage, safeSize);
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
