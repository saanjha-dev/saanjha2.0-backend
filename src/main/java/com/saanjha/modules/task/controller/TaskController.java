package com.saanjha.modules.task.controller;

import com.saanjha.modules.task.dto.TaskRequestDTOs.*;
import com.saanjha.modules.task.dto.TaskResponseDTOs.*;
import com.saanjha.modules.task.entity.TaskStatus;
import com.saanjha.modules.task.service.TaskService;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "7. Tasks", description = "The Work Management Engine: Kanban board, checklists, dependencies, and the Contribution Engine's data source")
public class TaskController {

    private final TaskService taskService;

    // ========================================================================
    // CREATION
    // ========================================================================

    @PostMapping("/v1/projects/{projectId}/tasks")
    @Idempotent(action = "create-task")
    @RateLimit(action = "create-task", baseLimit = 30, baseTimeSeconds = 3600)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfProject(#projectId, authentication.name)")
    @Operation(summary = "Allocate a Discrete Deliverable", description = "Requires an Idempotency-Key header.")
    public ResponseEntity<ApiEnvelope<TaskResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(projectId, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    // ========================================================================
    // READS / READ MODELS
    // ========================================================================

    @GetMapping("/v1/projects/{projectId}/tasks")
    @RateLimit(action = "search-tasks", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfProject(#projectId, authentication.name)")
    @Operation(summary = "Search Tasks", description = "Status/priority/assignee/creator/date-range/keyword filtering, paginated. Covers Backlog, Review Queue, Blocked Tasks, and Completed Tasks views via the status parameter — no bespoke endpoint per view.")
    public ResponseEntity<ApiEnvelope<List<TaskSummaryResponse>>> search(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TaskSearchCriteria criteria = new TaskSearchCriteria(status, priority, assigneeId, null, createdBy, createdAfter, createdBefore, keyword);
        Page<TaskSummaryResponse> result = taskService.search(projectId, criteria, buildPageable(page, size, "createdAt"));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/projects/{projectId}/tasks/board")
    @RateLimit(action = "get-task-board", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfProject(#projectId, authentication.name)")
    @Operation(summary = "Get Task Board", description = "Tasks grouped by status column, so the frontend doesn't assemble a Kanban board from separate list calls.")
    public ResponseEntity<ApiEnvelope<BoardResponse>> getBoard(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) String keyword) {
        TaskSearchCriteria criteria = new TaskSearchCriteria(null, priority, assigneeId, null, createdBy, null, null, keyword);
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getBoard(projectId, criteria)));
    }

    @GetMapping("/v1/projects/{projectId}/tasks/analytics")
    @RateLimit(action = "get-task-analytics", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfProject(#projectId, authentication.name)")
    @Operation(summary = "Get Task Analytics Dashboard", description = "Cycle time, lead time, completion rate — computed live, not incrementally cached (see TaskService javadoc for why).")
    public ResponseEntity<ApiEnvelope<TaskAnalyticsResponse>> getAnalytics(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getAnalytics(projectId)));
    }

    @GetMapping("/v1/tasks/mine")
    @RateLimit(action = "list-my-tasks", baseLimit = 30)
    @Operation(summary = "My Tasks", description = "Across all projects. Covers 'Assigned Tasks' via the optional status filter.")
    public ResponseEntity<ApiEnvelope<List<TaskSummaryResponse>>> listMine(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TaskSummaryResponse> result = taskService.listMyTasks(SecurityUtils.getCurrentUserId(), status, buildPageable(page, size, "dueDate"));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/tasks/{id}")
    @RateLimit(action = "get-task", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Task Detail")
    public ResponseEntity<ApiEnvelope<TaskResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getTask(id)));
    }

    @GetMapping("/v1/tasks/{id}/history")
    @RateLimit(action = "get-task-history", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Task History", description = "The append-only field-level audit trail.")
    public ResponseEntity<ApiEnvelope<List<TaskHistoryResponse>>> getHistory(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TaskHistoryResponse> result = taskService.getHistory(id, PageRequest.of(page, Math.min(size, 50)));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/tasks/{id}/activity")
    @RateLimit(action = "get-task-activity", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Task Activity Feed", description = "The user-facing narrative feed, distinct from History's audit shape.")
    public ResponseEntity<ApiEnvelope<List<TaskActivityResponse>>> getActivity(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TaskActivityResponse> result = taskService.getActivity(id, PageRequest.of(page, Math.min(size, 50)));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // UPDATE / MOVE / ASSIGNMENT
    // ========================================================================

    @PatchMapping("/v1/tasks/{id}")
    @RateLimit(action = "update-task", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Update Task Fields")
    public ResponseEntity<ApiEnvelope<TaskResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.updateTask(id, SecurityUtils.getCurrentUserId(), request)));
    }

    @PatchMapping("/v1/tasks/{id}/move")
    @RateLimit(action = "move-task", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Move Task", description = "Advances the Kanban state machine. Blocked tasks structurally cannot move directly to DONE.")
    public ResponseEntity<ApiEnvelope<TaskResponse>> move(@PathVariable UUID id, @Valid @RequestBody MoveTaskRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.move(id, SecurityUtils.getCurrentUserId(), request)));
    }

    @PatchMapping("/v1/tasks/{id}/assign")
    @RateLimit(action = "assign-task", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Assign Task", description = "Only active team members may be assigned; validated live against Team.")
    public ResponseEntity<ApiEnvelope<TaskResponse>> assign(@PathVariable UUID id, @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.assign(id, SecurityUtils.getCurrentUserId(), request.assigneeId())));
    }

    @PatchMapping("/v1/tasks/{id}/unassign")
    @RateLimit(action = "unassign-task", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Unassign Task")
    public ResponseEntity<ApiEnvelope<TaskResponse>> unassign(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.unassign(id, SecurityUtils.getCurrentUserId(), reason)));
    }

    @PostMapping("/v1/tasks/{id}/log-hours")
    @RateLimit(action = "log-hours", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Log Actual Hours")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> logHours(@PathVariable UUID id, @Valid @RequestBody LogHoursRequest request) {
        taskService.logHours(id, SecurityUtils.getCurrentUserId(), request.hours());
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Hours logged.")));
    }

    // ========================================================================
    // CHECKLIST
    // ========================================================================

    @GetMapping("/v1/tasks/{id}/checklist")
    @RateLimit(action = "get-checklist", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Checklist", description = "Items in their stored order — lets the frontend render an existing checklist after a page refresh.")
    public ResponseEntity<ApiEnvelope<List<ChecklistItemResponse>>> getChecklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getChecklist(id)));
    }

    @PostMapping("/v1/tasks/{id}/checklist")
    @RateLimit(action = "add-checklist-item", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Add Checklist Item")
    public ResponseEntity<ApiEnvelope<ChecklistItemResponse>> addChecklistItem(
            @PathVariable UUID id, @Valid @RequestBody AddChecklistItemRequest request) {
        ChecklistItemResponse response = taskService.addChecklistItem(id, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @DeleteMapping("/v1/tasks/{id}/checklist/{itemId}")
    @RateLimit(action = "remove-checklist-item", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Remove Checklist Item")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> removeChecklistItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        taskService.removeChecklistItem(id, itemId);
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Checklist item removed.")));
    }

    @PatchMapping("/v1/tasks/{id}/checklist/{itemId}/complete")
    @RateLimit(action = "complete-checklist-item", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Complete or Un-complete a Checklist Item")
    public ResponseEntity<ApiEnvelope<ChecklistItemResponse>> completeChecklistItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestParam(defaultValue = "true") boolean completed) {
        ChecklistItemResponse response = taskService.completeChecklistItem(id, itemId, SecurityUtils.getCurrentUserId(), completed);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PatchMapping("/v1/tasks/{id}/checklist/{itemId}/reorder")
    @RateLimit(action = "reorder-checklist-item", baseLimit = 60)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Reorder Checklist Item")
    public ResponseEntity<ApiEnvelope<List<ChecklistItemResponse>>> reorderChecklistItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ReorderChecklistItemRequest request) {
        List<ChecklistItemResponse> response = taskService.reorderChecklistItem(id, itemId, request.position());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // LABELS
    // ========================================================================

    @PostMapping("/v1/tasks/{id}/labels")
    @RateLimit(action = "add-label", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Add Label")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> addLabel(@PathVariable UUID id, @Valid @RequestBody AddLabelRequest request) {
        taskService.addLabel(id, request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(new TaskMutationResponse("Label added.")));
    }

    @DeleteMapping("/v1/tasks/{id}/labels/{labelName}")
    @RateLimit(action = "remove-label", baseLimit = 30)
    @PreAuthorize("@taskSecurityGuard.canEdit(authentication, #id)")
    @Operation(summary = "Remove Label")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> removeLabel(@PathVariable UUID id, @PathVariable String labelName) {
        taskService.removeLabel(id, labelName);
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Label removed.")));
    }

    // ========================================================================
    // DEPENDENCIES
    // ========================================================================

    @PostMapping("/v1/tasks/{id}/dependencies")
    @RateLimit(action = "add-dependency", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Add Dependency", description = "BLOCKS/PARENT/CHILD are checked for circularity before creation.")
    public ResponseEntity<ApiEnvelope<DependencyResponse>> addDependency(
            @PathVariable UUID id, @Valid @RequestBody AddDependencyRequest request) {
        DependencyResponse response = taskService.addDependency(id, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @DeleteMapping("/v1/tasks/{id}/dependencies/{dependencyId}")
    @RateLimit(action = "remove-dependency", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Remove Dependency")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> removeDependency(@PathVariable UUID id, @PathVariable UUID dependencyId) {
        taskService.removeDependency(id, dependencyId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Dependency removed.")));
    }

    @GetMapping("/v1/tasks/{id}/dependencies")
    @RateLimit(action = "get-dependencies", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Dependencies")
    public ResponseEntity<ApiEnvelope<List<DependencyResponse>>> getDependencies(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getDependencies(id)));
    }

    // ========================================================================
    // WATCHERS
    // ========================================================================

    @PostMapping("/v1/tasks/{id}/watch")
    @RateLimit(action = "watch-task", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Watch Task")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> watch(@PathVariable UUID id) {
        taskService.watch(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Now watching.")));
    }

    @DeleteMapping("/v1/tasks/{id}/watch")
    @RateLimit(action = "unwatch-task", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Unwatch Task")
    public ResponseEntity<ApiEnvelope<TaskMutationResponse>> unwatch(@PathVariable UUID id) {
        taskService.unwatch(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new TaskMutationResponse("Stopped watching.")));
    }

    @GetMapping("/v1/tasks/{id}/watchers")
    @RateLimit(action = "get-watchers", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Get Watchers")
    public ResponseEntity<ApiEnvelope<List<WatcherResponse>>> getWatchers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getWatchers(id)));
    }

    // ========================================================================
    // ATTACHMENTS (metadata only)
    // ========================================================================

    @PostMapping("/v1/tasks/{id}/attachments")
    @RateLimit(action = "add-attachment", baseLimit = 20)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "Attach File Metadata", description = "Task stores only metadata (name, size, checksum, storage URL). Upload the actual bytes to Cloudinary first, then register the reference here.")
    public ResponseEntity<ApiEnvelope<AttachmentResponse>> addAttachment(
            @PathVariable UUID id, @Valid @RequestBody AddAttachmentRequest request) {
        AttachmentResponse response = taskService.addAttachment(id, SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(201).body(ApiEnvelope.success(response));
    }

    @GetMapping("/v1/tasks/{id}/attachments")
    @RateLimit(action = "get-attachments", baseLimit = 30)
    @PreAuthorize("hasAuthority('task:moderate') or @taskGuard.isTeamMemberOfTask(#id, authentication.name)")
    @Operation(summary = "List Attachments")
    public ResponseEntity<ApiEnvelope<List<AttachmentResponse>>> getAttachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(taskService.getAttachments(id)));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Pageable buildPageable(int page, int size, String sortField) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField));
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
