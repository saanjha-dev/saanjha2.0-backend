package com.saanjha.modules.project.controller;

import com.saanjha.modules.project.dto.ProjectRequestDTOs.*;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.*;
import com.saanjha.modules.project.service.ProjectService;
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
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "3. Projects", description = "Project metadata, scope, structural requirements, and lifecycle status")
public class ProjectController {

    private final ProjectService projectService;

    // ========================================================================
    // CREATION
    // ========================================================================

    @PostMapping
    @Idempotent(action = "create-project")
    @RateLimit(action = "create-project", baseLimit = 1, baseTimeSeconds = 3600)
    @PreAuthorize("hasAuthority('project:create')")
    @Operation(summary = "Initialize a Project", description = "Creates a new project in DRAFT status. Requires an Idempotency-Key header.")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    // ========================================================================
    // READS
    // ========================================================================

    @GetMapping("/{id}")
    @RateLimit(action = "get-project", baseLimit = 60)
    @Operation(summary = "Get Project Detail", description = "Fetches a project. DRAFT and ARCHIVED projects are only visible to their Lead.")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> getProject(@PathVariable UUID id) {
        UUID requestingUserId = resolveOptionalUserId();
        ProjectResponse response = projectService.getProject(id, requestingUserId);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @GetMapping("/slug/{slug}")
    @RateLimit(action = "get-project-by-slug", baseLimit = 60)
    @Operation(summary = "Get Project by Slug", description = "Fetches a public project via its vanity URL slug.")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> getProjectBySlug(@PathVariable String slug) {
        ProjectResponse response = projectService.getProjectBySlug(slug);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @GetMapping("/mine")
    @RateLimit(action = "list-my-projects", baseLimit = 30)
    @Operation(summary = "List My Projects", description = "Paginated list of projects the authenticated user leads, in any status.")
    public ResponseEntity<ApiEnvelope<List<ProjectSummaryResponse>>> listMyProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = buildPageable(page, size);
        Page<ProjectSummaryResponse> result = projectService.listMyProjects(SecurityUtils.getCurrentUserId(), pageable);
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping
    @RateLimit(action = "list-public-projects", baseLimit = 60)
    @Operation(summary = "Browse Recruiting Projects", description =
            "Minimal public listing of actively recruiting projects. A placeholder for the future " +
            "Discovery module's full-text/fuzzy search — kept intentionally simple.")
    public ResponseEntity<ApiEnvelope<List<ProjectSummaryResponse>>> listPublicProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = buildPageable(page, size);
        Page<ProjectSummaryResponse> result = projectService.listPublicRecruitingProjects(pageable);
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/{id}/status-history")
    @RateLimit(action = "get-status-history", baseLimit = 30)
    @Operation(summary = "Get Status History", description = "Returns the append-only ledger of lifecycle transitions for this project.")
    public ResponseEntity<ApiEnvelope<List<ProjectStatusLogResponse>>> getStatusHistory(@PathVariable UUID id) {
        UUID requestingUserId = resolveOptionalUserId();
        List<ProjectStatusLogResponse> history = projectService.getStatusHistory(id, requestingUserId);
        return ResponseEntity.ok(ApiEnvelope.success(history));
    }

    // ========================================================================
    // SCOPE MUTATION
    // ========================================================================

    @PatchMapping("/{id}")
    @RateLimit(action = "update-project-scope", baseLimit = 15)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Update Project Scope", description = "Updates title, description, category, visibility, or team size cap. Blocked once COMPLETED or ARCHIVED.")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> updateScope(
            @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse response = projectService.updateScope(id, request);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // STATE MACHINE
    // ========================================================================

    @PatchMapping("/{id}/status")
    @RateLimit(action = "update-project-status", baseLimit = 15)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Advance Project Status", description = "Transitions the project's lifecycle state machine (DRAFT -> RECRUITING -> IN_PROGRESS -> COMPLETED, or any state -> ARCHIVED).")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateProjectStatusRequest request) {
        ProjectResponse response = projectService.transitionStatus(id, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // REQUIREMENTS
    // ========================================================================

    @PostMapping("/{id}/requirements")
    @RateLimit(action = "add-requirement", baseLimit = 20)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Add a Skill Requirement", description = "Adds a structural requirement (skill, level, open slots) the Lead is recruiting for.")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> addRequirement(
            @PathVariable UUID id, @Valid @RequestBody AddRequirementRequest request) {
        ProjectResponse response = projectService.addRequirement(id, request);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @DeleteMapping("/{id}/requirements/{requirementId}")
    @RateLimit(action = "remove-requirement", baseLimit = 20)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Remove a Skill Requirement")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> removeRequirement(
            @PathVariable UUID id, @PathVariable UUID requirementId) {
        ProjectResponse response = projectService.removeRequirement(id, requirementId);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // TAGS
    // ========================================================================

    @PostMapping("/{id}/tags")
    @RateLimit(action = "add-tag", baseLimit = 20)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Add a Discovery Tag")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> addTag(
            @PathVariable UUID id, @Valid @RequestBody AddTagRequest request) {
        ProjectResponse response = projectService.addTag(id, request);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @RateLimit(action = "remove-tag", baseLimit = 20)
    @PreAuthorize("hasAuthority('project:moderate') or @projectGuard.isLead(#id, authentication.name)")
    @Operation(summary = "Remove a Discovery Tag")
    public ResponseEntity<ApiEnvelope<ProjectResponse>> removeTag(
            @PathVariable UUID id, @PathVariable UUID tagId) {
        ProjectResponse response = projectService.removeTag(id, tagId);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /** Read endpoints are publicly reachable (see SecurityConfig); resolve the caller only if a valid token was presented. */
    private UUID resolveOptionalUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception ex) {
            return null;
        }
    }

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
