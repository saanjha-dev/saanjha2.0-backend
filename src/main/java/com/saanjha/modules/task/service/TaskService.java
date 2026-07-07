package com.saanjha.modules.task.service;

import com.saanjha.modules.task.dto.TaskRequestDTOs.*;
import com.saanjha.modules.task.dto.TaskResponseDTOs.*;
import com.saanjha.modules.task.entity.*;
import com.saanjha.modules.task.event.TaskEvents.*;
import com.saanjha.modules.task.repository.*;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.modules.team.dto.TeamResponseDTOs.TeamResponse;
import com.saanjha.modules.team.service.TeamService;
import com.saanjha.modules.team.service.TeamSecurityGuard;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.security.HtmlSanitizer;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the Task aggregate: lifecycle, assignment, checklists,
 * dependencies, watchers, attachments (metadata only), and the read models
 * the frontend needs. Every project/team-state check goes through the
 * sanctioned service-interface calls (`ProjectService.getSnapshot`,
 * `TeamService.getTeamByProject`, `TeamSecurityGuard.isMemberOfProjectsTeam`)
 * — never a direct read of another module's schema.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /** MES H.2 #7: "Users are hard-capped at 3 IN_PROGRESS tasks at any given moment to prevent bottlenecking." */
    private static final int MAX_IN_PROGRESS_PER_ASSIGNEE = 3;

    private static final int MAX_DEPENDENCY_TRAVERSAL = 500;

    private final TaskRepository taskRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final TaskLabelRepository labelRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final TaskWatcherRepository watcherRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskHistoryRepository historyRepository;
    private final TaskActivityRepository activityRepository;
    private final ProjectService projectService;
    private final TeamService teamService;
    private final TeamSecurityGuard teamSecurityGuard;
    private final ApplicationEventPublisher eventPublisher;

    // ========================================================================
    // CREATION
    // ========================================================================

    /**
     * Tasks may be created as early as RECRUITING (backlog planning ahead of
     * the team locking in — Team already exists once a project publishes,
     * see Team's own self-seeding on ProjectPublishedEvent) through
     * IN_PROGRESS. Only COMPLETED/ARCHIVED projects and ARCHIVED/DISSOLVED/
     * LOCKED teams block creation — see {@code assertProjectAcceptingWork}/
     * {@code assertTeamActive}. A DRAFT project has no Team row yet at all,
     * so {@code assertTeamActive} fails closed (NOT_FOUND) for it without
     * needing a separate DRAFT-specific check.
     */
    @Transactional
    public TaskResponse createTask(UUID projectId, UUID reporterId, CreateTaskRequest request) {
        assertProjectAcceptingWork(projectId);
        assertTeamActive(projectId);

        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description() != null ? HtmlSanitizer.sanitize(request.description()) : null);
        task.setType(TaskType.valueOf(request.type()));
        task.setPriority(request.priority() != null ? TaskPriority.valueOf(request.priority()) : TaskPriority.MEDIUM);
        task.setReporterId(reporterId);
        task.setStoryPoints(request.storyPoints());
        task.setEstimatedHours(request.estimatedHours());
        task.setDueDate(request.dueDate());
        task.setStatus(TaskStatus.BACKLOG);

        if (request.assigneeId() != null) {
            assertValidAssignee(projectId, request.assigneeId());
            task.setAssigneeId(request.assigneeId());
        }

        task = taskRepository.save(task);
        recordActivity(task.getId(), TaskActivityType.CREATED, reporterId, "Task created.");
        eventPublisher.publishEvent(new TaskCreatedEvent(task.getId(), projectId, reporterId, task.getType().name(), task.getPriority().name(), Instant.now()));

        return mapToResponse(task);
    }

    // ========================================================================
    // READS
    // ========================================================================

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        return mapToResponse(getTaskOrThrow(taskId));
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> search(UUID projectId, TaskSearchCriteria criteria, Pageable pageable) {
        Specification<Task> spec = buildSearchSpecification(projectId, criteria);
        return taskRepository.findAll(spec, pageable).map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> listMyTasks(UUID assigneeId, TaskStatus statusFilter, Pageable pageable) {
        Page<Task> page = statusFilter != null
                ? taskRepository.findByAssigneeIdAndStatus(assigneeId, statusFilter, pageable)
                : taskRepository.findByAssigneeId(assigneeId, pageable);
        return page.map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(UUID projectId) {
        Map<String, List<TaskSummaryResponse>> columns = new LinkedHashMap<>();
        for (TaskStatus status : List.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS,
                TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.DONE)) {
            Page<Task> page = taskRepository.findByProjectIdAndStatus(projectId, status,
                    org.springframework.data.domain.PageRequest.of(0, 200));
            columns.put(status.name(), page.getContent().stream().map(this::mapToSummary).toList());
        }
        return new BoardResponse(projectId, columns);
    }

    /**
     * Analytics computed live via indexed aggregate queries, deliberately NOT
     * incrementally-cached counters the way Team's metrics are. Team's
     * roster is read on nearly every authorization check (a genuine hot
     * path), which justified paying for incremental-maintenance complexity.
     * A project's task dashboard is a deliberate, infrequent "view stats"
     * action — an indexed `COUNT`/`AVG` query is simpler and cheap enough at
     * the bounded scale of one project's tasks.
     */
    @Transactional(readOnly = true)
    public TaskAnalyticsResponse getAnalytics(UUID projectId) {
        long created = taskRepository.countByProjectId(projectId);
        long completed = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
        double completionRate = created == 0 ? 0.0 : (completed * 100.0 / created);

        Double avgCycleSeconds = taskRepository.averageCycleTimeSeconds(projectId);
        Double avgLeadSeconds = taskRepository.averageLeadTimeSeconds(projectId);

        Map<String, Long> counts = new HashMap<>();
        for (TaskRepository.StatusCount row : taskRepository.countByStatusForProject(projectId)) {
            counts.put(row.getStatus().name(), row.getCount());
        }

        return new TaskAnalyticsResponse(
                projectId, created, completed, completionRate,
                avgCycleSeconds != null ? avgCycleSeconds / 3600.0 : null,
                avgLeadSeconds != null ? avgLeadSeconds / 3600.0 : null,
                counts
        );
    }

    @Transactional(readOnly = true)
    public Page<TaskHistoryResponse> getHistory(UUID taskId, Pageable pageable) {
        return historyRepository.findByTaskIdOrderByChangedAtDesc(taskId, pageable)
                .map(h -> new TaskHistoryResponse(h.getFieldChanged(), h.getOldValue(), h.getNewValue(), h.getChangedBy(), h.getReason(), h.getChangedAt()));
    }

    @Transactional(readOnly = true)
    public Page<TaskActivityResponse> getActivity(UUID taskId, Pageable pageable) {
        return activityRepository.findByTaskIdOrderByOccurredAtDesc(taskId, pageable)
                .map(a -> new TaskActivityResponse(a.getActivityType().name(), a.getActorId(), a.getSummary(), a.getOccurredAt()));
    }

    // ========================================================================
    // UPDATE (non-status fields)
    // ========================================================================

    @Transactional
    public TaskResponse updateTask(UUID taskId, UUID actingUserId, UpdateTaskRequest request) {
        Task task = getTaskOrThrow(taskId);
        assertProjectAcceptingWork(task.getProjectId());
        assertMutable(task);

        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(HtmlSanitizer.sanitize(request.description()));
        }
        if (request.type() != null) {
            task.setType(TaskType.valueOf(request.type()));
        }
        if (request.priority() != null) {
            task.setPriority(TaskPriority.valueOf(request.priority()));
        }
        if (request.storyPoints() != null) {
            task.setStoryPoints(request.storyPoints());
        }
        if (request.estimatedHours() != null && !request.estimatedHours().equals(task.getEstimatedHours())) {
            Double previous = task.getEstimatedHours();
            task.setEstimatedHours(request.estimatedHours());
            recordHistory(taskId, "estimatedHours", String.valueOf(previous), String.valueOf(request.estimatedHours()), actingUserId, null);
            eventPublisher.publishEvent(new EstimateChangedEvent(taskId, task.getProjectId(), previous, request.estimatedHours(), actingUserId, Instant.now()));
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        task = taskRepository.save(task);
        return mapToResponse(task);
    }

    @Transactional
    public void logHours(UUID taskId, UUID actingUserId, double hours) {
        Task task = getTaskOrThrow(taskId);
        assertProjectAcceptingWork(task.getProjectId());
        task.setActualHours(task.getActualHours() + hours);
        taskRepository.save(task);
        recordHistory(taskId, "actualHours", null, String.valueOf(task.getActualHours()), actingUserId, "Logged " + hours + "h.");
    }

    // ========================================================================
    // ASSIGNMENT
    // ========================================================================

    @Transactional
    public TaskResponse assign(UUID taskId, UUID actingUserId, UUID assigneeId) {
        Task task = taskRepository.findWithLockById(taskId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Task not found."));
        assertProjectAcceptingWork(task.getProjectId());
        assertMutable(task);
        assertValidAssignee(task.getProjectId(), assigneeId);

        task.setAssigneeId(assigneeId);
        task = taskRepository.save(task);

        recordHistory(taskId, "assigneeId", null, assigneeId.toString(), actingUserId, null);
        recordActivity(taskId, TaskActivityType.ASSIGNED, actingUserId, "Assigned to a new team member.");
        eventPublisher.publishEvent(new TaskAssignedEvent(taskId, task.getProjectId(), assigneeId, actingUserId, Instant.now()));

        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse unassign(UUID taskId, UUID actingUserId, String reason) {
        Task task = taskRepository.findWithLockById(taskId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Task not found."));
        assertProjectAcceptingWork(task.getProjectId());
        assertMutable(task);

        UUID previousAssignee = task.getAssigneeId();
        task.setAssigneeId(null);
        task = taskRepository.save(task);

        recordHistory(taskId, "assigneeId", previousAssignee != null ? previousAssignee.toString() : null, null, actingUserId, reason);
        recordActivity(taskId, TaskActivityType.UNASSIGNED, actingUserId, "Unassigned.");
        eventPublisher.publishEvent(new TaskUnassignedEvent(taskId, task.getProjectId(), previousAssignee, reason, Instant.now()));

        return mapToResponse(task);
    }

    // ========================================================================
    // STATE MACHINE
    // ========================================================================

    @Transactional
    public TaskResponse move(UUID taskId, UUID actingUserId, MoveTaskRequest request) {
        Task task = taskRepository.findWithLockById(taskId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Task not found."));
        assertProjectAcceptingWork(task.getProjectId());

        TaskStatus from = task.getStatus();
        TaskStatus to = TaskStatus.valueOf(request.targetStatus());
        TaskStatusTransitionValidator.assertLegal(from, to);

        runPreTransitionGuards(task, to);
        applyTransitionSideEffects(task, from, to, actingUserId, request.reason());
        task.setStatus(to);
        task = taskRepository.save(task);

        recordHistory(taskId, "status", from.name(), to.name(), actingUserId, request.reason());
        publishTransitionEvent(task, from, to, actingUserId, request.reason());

        return mapToResponse(task);
    }

    private void runPreTransitionGuards(Task task, TaskStatus target) {
        if (target == TaskStatus.IN_PROGRESS) {
            if (!task.isAssigned()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Assign this task to someone before starting work on it.");
            }
            long inProgressCount = taskRepository.countByAssigneeIdAndStatus(task.getAssigneeId(), TaskStatus.IN_PROGRESS);
            if (inProgressCount >= MAX_IN_PROGRESS_PER_ASSIGNEE) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "This assignee already has " + MAX_IN_PROGRESS_PER_ASSIGNEE + " tasks IN_PROGRESS. Finish or pause one first.");
            }
        }
    }

    private void applyTransitionSideEffects(Task task, TaskStatus from, TaskStatus to, UUID actingUserId, String reason) {
        switch (to) {
            case IN_PROGRESS -> {
                if (task.getStartedAt() == null) {
                    task.setStartedAt(Instant.now());
                }
                task.setBlockedReason(null);
            }
            case BLOCKED -> {
                if (reason == null || reason.isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, "A reason is required to block a task.");
                }
                task.setBlockedReason(reason);
            }
            case DONE -> {
                task.setCompletedAt(Instant.now());
                task.setBlockedReason(null);
            }
            case CANCELLED -> {
                task.setCancelledAt(Instant.now());
                task.setBlockedReason(null);
            }
            case ARCHIVED -> task.setArchivedAt(Instant.now());
            case TODO -> {
                if (from == TaskStatus.DONE || from == TaskStatus.CANCELLED) {
                    task.setCompletedAt(null);
                    task.setCancelledAt(null);
                }
                task.setBlockedReason(null);
            }
            default -> { /* BACKLOG, IN_REVIEW, DUPLICATE need no side-field updates beyond status itself */ }
        }
    }

    private void publishTransitionEvent(Task task, TaskStatus from, TaskStatus to, UUID actingUserId, String reason) {
        Instant now = Instant.now();
        switch (to) {
            case IN_PROGRESS -> {
                recordActivity(task.getId(), TaskActivityType.STATUS_CHANGED, actingUserId, "Started work.");
                eventPublisher.publishEvent(new TaskStartedEvent(task.getId(), task.getProjectId(), task.getAssigneeId(), now));
            }
            case BLOCKED -> {
                recordActivity(task.getId(), TaskActivityType.BLOCKED, actingUserId, "Blocked: " + reason);
                eventPublisher.publishEvent(new TaskBlockedEvent(task.getId(), task.getProjectId(), reason, now));
            }
            case IN_REVIEW -> {
                recordActivity(task.getId(), TaskActivityType.STATUS_CHANGED, actingUserId, "Moved to review.");
                eventPublisher.publishEvent(new TaskMovedToReviewEvent(task.getId(), task.getProjectId(), task.getAssigneeId(), now));
                if (from == TaskStatus.BLOCKED) {
                    eventPublisher.publishEvent(new TaskUnblockedEvent(task.getId(), task.getProjectId(), now));
                }
            }
            case DONE -> {
                recordActivity(task.getId(), TaskActivityType.STATUS_CHANGED, actingUserId, "Marked done.");
                eventPublisher.publishEvent(new TaskCompletedEvent(
                        task.getId(), task.getProjectId(), task.getAssigneeId(), task.getReporterId(),
                        task.getStoryPoints(), null, task.getEstimatedHours(), task.getActualHours(),
                        actingUserId, now));
            }
            case CANCELLED -> {
                recordActivity(task.getId(), TaskActivityType.STATUS_CHANGED, actingUserId, "Cancelled.");
                eventPublisher.publishEvent(new TaskCancelledEvent(task.getId(), task.getProjectId(), reason, now));
            }
            case ARCHIVED -> {
                recordActivity(task.getId(), TaskActivityType.ARCHIVED, actingUserId, "Archived.");
                eventPublisher.publishEvent(new TaskArchivedEvent(task.getId(), task.getProjectId(), now));
            }
            case TODO -> {
                if (from == TaskStatus.BLOCKED) {
                    eventPublisher.publishEvent(new TaskUnblockedEvent(task.getId(), task.getProjectId(), now));
                }
                if (from == TaskStatus.DONE || from == TaskStatus.CANCELLED) {
                    recordActivity(task.getId(), TaskActivityType.STATUS_CHANGED, actingUserId, "Reopened.");
                    eventPublisher.publishEvent(new TaskReopenedEvent(task.getId(), task.getProjectId(), actingUserId, now));
                }
            }
            default -> { /* BACKLOG, DUPLICATE: no dedicated event beyond the History row already written */ }
        }
    }

    // ========================================================================
    // CHECKLIST
    // ========================================================================

    @Transactional
    public ChecklistItemResponse addChecklistItem(UUID taskId, UUID actingUserId, AddChecklistItemRequest request) {
        Task task = getTaskOrThrow(taskId);
        assertProjectAcceptingWork(task.getProjectId());
        assertMutable(task);

        int nextPosition = (int) checklistItemRepository.countByTask_Id(taskId);
        ChecklistItem item = new ChecklistItem();
        item.setTask(task);
        item.setText(HtmlSanitizer.sanitize(request.text()));
        item.setPosition(nextPosition);
        item = checklistItemRepository.save(item);

        recordActivity(taskId, TaskActivityType.CHECKLIST_ITEM_ADDED, actingUserId, "Added checklist item.");
        return mapChecklistItem(item);
    }

    @Transactional
    public void removeChecklistItem(UUID taskId, UUID itemId) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Checklist item not found."));
        if (!item.getTask().getId().equals(taskId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Checklist item not found on this task.");
        }
        checklistItemRepository.delete(item);
    }

    @Transactional
    public ChecklistItemResponse completeChecklistItem(UUID taskId, UUID itemId, UUID actingUserId, boolean completed) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Checklist item not found."));
        if (!item.getTask().getId().equals(taskId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Checklist item not found on this task.");
        }

        item.setCompleted(completed);
        item.setCompletedAt(completed ? Instant.now() : null);
        item.setCompletedBy(completed ? actingUserId : null);
        item = checklistItemRepository.save(item);

        if (completed) {
            recordActivity(taskId, TaskActivityType.CHECKLIST_ITEM_COMPLETED, actingUserId, "Completed a checklist item.");

            long total = checklistItemRepository.countByTask_Id(taskId);
            long done = checklistItemRepository.countByTask_IdAndCompletedTrue(taskId);
            if (total > 0 && total == done) {
                Task task = getTaskOrThrow(taskId);
                eventPublisher.publishEvent(new ChecklistCompletedEvent(taskId, task.getProjectId(), Instant.now()));
            }
        }

        return mapChecklistItem(item);
    }

    @Transactional
    public List<ChecklistItemResponse> reorderChecklistItem(UUID taskId, UUID itemId, int newPosition) {
        List<ChecklistItem> items = checklistItemRepository.findByTask_IdOrderByPositionAsc(taskId);
        ChecklistItem target = items.stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Checklist item not found on this task."));

        items.remove(target);
        int clampedPosition = Math.max(0, Math.min(newPosition, items.size()));
        items.add(clampedPosition, target);

        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
        checklistItemRepository.saveAll(items);

        return items.stream().map(this::mapChecklistItem).toList();
    }

    // ========================================================================
    // LABELS
    // ========================================================================

    @Transactional
    public void addLabel(UUID taskId, AddLabelRequest request) {
        Task task = getTaskOrThrow(taskId);
        String name = request.name().trim();
        if (labelRepository.existsByTask_IdAndNameIgnoreCase(taskId, name)) {
            throw new AppException(ErrorCode.CONFLICT, "This label already exists on the task.");
        }
        labelRepository.save(new TaskLabel(task, name, request.scope() != null ? request.scope() : "PROJECT"));
    }

    @Transactional
    public void removeLabel(UUID taskId, UUID labelId) {
        TaskLabel label = labelRepository.findById(labelId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Label not found."));
        if (!label.getTask().getId().equals(taskId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Label not found on this task.");
        }
        labelRepository.delete(label);
    }

    // ========================================================================
    // DEPENDENCIES
    // ========================================================================

    @Transactional
    public DependencyResponse addDependency(UUID taskId, UUID actingUserId, AddDependencyRequest request) {
        Task task = getTaskOrThrow(taskId);
        UUID relatedTaskId = request.relatedTaskId();
        DependencyType type = DependencyType.valueOf(request.type());

        if (taskId.equals(relatedTaskId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A task cannot depend on itself.");
        }
        Task relatedTask = getTaskOrThrow(relatedTaskId);
        if (!relatedTask.getProjectId().equals(task.getProjectId())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Dependencies must be within the same project.");
        }
        if (dependencyRepository.existsByTaskIdAndRelatedTaskIdAndType(taskId, relatedTaskId, type)) {
            throw new AppException(ErrorCode.CONFLICT, "This dependency already exists.");
        }

        if (requiresCycleCheck(type) && wouldCreateCycle(relatedTaskId, taskId, type)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "This dependency would create a circular relationship.");
        }

        TaskDependency dependency = dependencyRepository.save(new TaskDependency(taskId, relatedTaskId, type, actingUserId));
        maybeCreateInverse(taskId, relatedTaskId, type, actingUserId);

        recordActivity(taskId, TaskActivityType.DEPENDENCY_ADDED, actingUserId, "Added a " + type + " dependency.");
        eventPublisher.publishEvent(new TaskDependencyCreatedEvent(taskId, task.getProjectId(), relatedTaskId, type.name(), Instant.now()));

        return new DependencyResponse(dependency.getId(), relatedTaskId, type.name(), dependency.getCreatedAt());
    }

    @Transactional
    public void removeDependency(UUID taskId, UUID dependencyId, UUID actingUserId) {
        TaskDependency dependency = dependencyRepository.findById(dependencyId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Dependency not found."));
        if (!dependency.getTaskId().equals(taskId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Dependency not found on this task.");
        }

        Task task = getTaskOrThrow(taskId);
        dependencyRepository.delete(dependency);
        recordActivity(taskId, TaskActivityType.DEPENDENCY_REMOVED, actingUserId, "Removed a " + dependency.getType() + " dependency.");
        eventPublisher.publishEvent(new TaskDependencyRemovedEvent(taskId, task.getProjectId(), dependency.getRelatedTaskId(), dependency.getType().name(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> getDependencies(UUID taskId) {
        return dependencyRepository.findByTaskId(taskId).stream()
                .map(d -> new DependencyResponse(d.getId(), d.getRelatedTaskId(), d.getType().name(), d.getCreatedAt()))
                .toList();
    }

    private boolean requiresCycleCheck(DependencyType type) {
        return type == DependencyType.BLOCKS || type == DependencyType.PARENT || type == DependencyType.CHILD;
    }

    /**
     * Bounded BFS: does a path already exist from {@code fromTaskId} back to
     * {@code toTaskId} following same-typed edges? If so, adding
     * {@code toTaskId -> fromTaskId} would close a cycle. Capped at
     * {@link #MAX_DEPENDENCY_TRAVERSAL} visited nodes as a safety valve
     * against pathological data — hitting the cap is treated as "assume a
     * cycle" (reject), the conservative choice, not "assume it's fine."
     */
    private boolean wouldCreateCycle(UUID fromTaskId, UUID toTaskId, DependencyType type) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(fromTaskId);

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_DEPENDENCY_TRAVERSAL) {
                return true; // Conservative: too large to fully verify, assume unsafe.
            }
            UUID current = queue.poll();
            if (current.equals(toTaskId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (TaskDependency edge : dependencyRepository.findByTaskIdAndType(current, type)) {
                queue.add(edge.getRelatedTaskId());
            }
        }
        return false;
    }

    private void maybeCreateInverse(UUID taskId, UUID relatedTaskId, DependencyType type, UUID actingUserId) {
        DependencyType inverse = switch (type) {
            case BLOCKS -> DependencyType.BLOCKED_BY;
            case BLOCKED_BY -> DependencyType.BLOCKS;
            case PARENT -> DependencyType.CHILD;
            case CHILD -> DependencyType.PARENT;
            default -> null; // DUPLICATE_OF and RELATES_TO are symmetric/self-describing, no inverse row needed.
        };
        if (inverse != null && !dependencyRepository.existsByTaskIdAndRelatedTaskIdAndType(relatedTaskId, taskId, inverse)) {
            dependencyRepository.save(new TaskDependency(relatedTaskId, taskId, inverse, actingUserId));
        }
    }

    // ========================================================================
    // WATCHERS
    // ========================================================================

    @Transactional
    public void watch(UUID taskId, UUID userId) {
        getTaskOrThrow(taskId);
        if (!watcherRepository.existsByTaskIdAndUserId(taskId, userId)) {
            watcherRepository.save(new TaskWatcher(taskId, userId));
        }
    }

    @Transactional
    public void unwatch(UUID taskId, UUID userId) {
        watcherRepository.findByTaskIdAndUserId(taskId, userId).ifPresent(watcherRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<WatcherResponse> getWatchers(UUID taskId) {
        return watcherRepository.findByTaskId(taskId).stream()
                .map(w -> new WatcherResponse(w.getUserId(), w.getCreatedAt()))
                .toList();
    }

    // ========================================================================
    // ATTACHMENTS (metadata only)
    // ========================================================================

    @Transactional
    public AttachmentResponse addAttachment(UUID taskId, UUID actingUserId, AddAttachmentRequest request) {
        getTaskOrThrow(taskId);
        TaskAttachment attachment = attachmentRepository.save(new TaskAttachment(
                taskId, request.fileName(), request.sizeBytes(), request.contentType(),
                request.storageUrl(), request.checksum(), actingUserId));
        return mapAttachment(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(UUID taskId) {
        return attachmentRepository.findByTaskId(taskId).stream().map(this::mapAttachment).toList();
    }

    // ========================================================================
    // EVENT-LISTENER-TRIGGERED (internal, idempotent, no controller route)
    // ========================================================================

    /** Team member removed from the project — unassign every task still assigned to them. */
    @Transactional
    public void handleMemberRemoved(UUID projectId, UUID removedUserId) {
        List<Task> affected = taskRepository.findByProjectIdAndAssigneeIdAndStatusIn(
                projectId, removedUserId,
                List.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.IN_REVIEW));
        for (Task task : affected) {
            try {
                unassign(task.getId(), TaskHistory.SYSTEM_ACTOR_ID, "Unassigned automatically: no longer a team member.");
            } catch (Exception ignored) {
                // One bad row must never abort the rest of the batch.
            }
        }
    }

    /** Project reached COMPLETED/ARCHIVED — force every non-archived task to ARCHIVED, bypassing the normal transition graph (same precedent as Team's archiveWithTeam). */
    @Transactional
    public void archiveAllForProject(UUID projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.ARCHIVED) {
                continue;
            }
            TaskStatus from = task.getStatus();
            task.setArchivedAt(Instant.now());
            task.setStatus(TaskStatus.ARCHIVED);
            taskRepository.save(task);
            recordHistory(task.getId(), "status", from.name(), TaskStatus.ARCHIVED.name(), TaskHistory.SYSTEM_ACTOR_ID, "Project reached a terminal state.");
            recordActivity(task.getId(), TaskActivityType.ARCHIVED, TaskHistory.SYSTEM_ACTOR_ID, "Archived automatically with the project.");
            eventPublisher.publishEvent(new TaskArchivedEvent(task.getId(), projectId, Instant.now()));
        }
    }

    // ========================================================================
    // GUARDS & HELPERS
    // ========================================================================

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Task not found."));
    }

    private void assertMutable(Task task) {
        if (!task.isMutable()) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "This task is archived and can no longer be modified.");
        }
    }

    /** "Completed projects cannot modify tasks" — checked live against Project's own cache, never assumed. */
    private void assertProjectAcceptingWork(UUID projectId) {
        ProjectSnapshot snapshot = projectService.getSnapshot(projectId);
        if ("COMPLETED".equals(snapshot.status()) || "ARCHIVED".equals(snapshot.status())) {
            throw new AppException(ErrorCode.PROJECT_READ_ONLY, "This project is " + snapshot.status() + " and its tasks can no longer be modified.");
        }
    }

    /** "Archived teams cannot create tasks" — extended to any non-ACTIVE team status, not just ARCHIVED. */
    private void assertTeamActive(UUID projectId) {
        TeamResponse team = teamService.getTeamByProject(projectId);
        if (!"ACTIVE".equals(team.status()) && !"CREATED".equals(team.status())) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "This project's team is " + team.status() + " and cannot accept new tasks.");
        }
    }

    /** "Only Team members may be assigned" — checked live, never cached, since Team is the sole authority on membership. */
    private void assertValidAssignee(UUID projectId, UUID assigneeId) {
        if (!teamSecurityGuard.isMemberOfProjectsTeam(projectId, assigneeId.toString())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Only active team members can be assigned to a task.");
        }
    }

    private Specification<Task> buildSearchSpecification(UUID projectId, TaskSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));

            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), TaskStatus.valueOf(criteria.status())));
            }
            if (criteria.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), TaskPriority.valueOf(criteria.priority())));
            }
            if (criteria.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assigneeId"), criteria.assigneeId()));
            }
            if (criteria.createdBy() != null) {
                predicates.add(cb.equal(root.get("reporterId"), criteria.createdBy()));
            }
            if (criteria.createdAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdAfter()));
            }
            if (criteria.createdBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.createdBefore()));
            }
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            // Label filtering intentionally omitted from this Specification: it
            // requires a join to tsk_labels, which is a reasonable follow-up but
            // adds real complexity (duplicate rows without a DISTINCT) for a
            // filter that's lower-priority than the ones implemented here.
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void recordHistory(UUID taskId, String field, String oldValue, String newValue, UUID changedBy, String reason) {
        historyRepository.save(new TaskHistory(taskId, field, oldValue, newValue, changedBy, reason));
    }

    private void recordActivity(UUID taskId, TaskActivityType type, UUID actorId, String summary) {
        activityRepository.save(new TaskActivity(taskId, type, actorId, summary));
    }

    private TaskResponse mapToResponse(Task task) {
        List<String> labels = labelRepository.findByTask_Id(task.getId()).stream().map(TaskLabel::getName).sorted().toList();
        int checklistTotal = (int) checklistItemRepository.countByTask_Id(task.getId());
        int checklistCompleted = (int) checklistItemRepository.countByTask_IdAndCompletedTrue(task.getId());

        return new TaskResponse(
                task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(),
                task.getType().name(), task.getPriority().name(), task.getStatus().name(),
                task.getReporterId(), task.getAssigneeId(), task.getStoryPoints(), task.getEstimatedHours(),
                task.getActualHours(), task.getDueDate(), task.getBlockedReason(),
                task.getStartedAt(), task.getCompletedAt(), task.getArchivedAt(),
                labels, checklistTotal, checklistCompleted,
                task.getCreatedAt(), task.getUpdatedAt()
        );
    }

    private TaskSummaryResponse mapToSummary(Task task) {
        return new TaskSummaryResponse(
                task.getId(), task.getTitle(), task.getType().name(), task.getPriority().name(),
                task.getStatus().name(), task.getAssigneeId(), task.getDueDate(), task.getCreatedAt());
    }

    private ChecklistItemResponse mapChecklistItem(ChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getText(), item.isCompleted(), item.getPosition(), item.getCompletedAt(), item.getCompletedBy());
    }

    private AttachmentResponse mapAttachment(TaskAttachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getFileName(), attachment.getSizeBytes(),
                attachment.getContentType(), attachment.getStorageUrl(), attachment.getVirusScanStatus().name(),
                attachment.getUploadedBy(), attachment.getUploadedAt());
    }
}
