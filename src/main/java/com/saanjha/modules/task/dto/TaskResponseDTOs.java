package com.saanjha.modules.task.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskResponseDTOs {

    public record TaskResponse(
            UUID id,
            String taskKey,
            UUID projectId,
            String title,
            String description,
            String type,
            String priority,
            String status,
            UUID reporterId,
            UUID assigneeId,
            Integer storyPoints,
            Double estimatedHours,
            double actualHours,
            Instant dueDate,
            String blockedReason,
            Instant startedAt,
            Instant completedAt,
            Instant archivedAt,
            List<String> labels,
            int checklistTotal,
            int checklistCompleted,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TaskSummaryResponse(
            UUID id,
            String taskKey,
            String title,
            String type,
            String priority,
            String status,
            UUID assigneeId,
            Integer storyPoints,
            String blockedReason,
            Instant dueDate,
            Instant createdAt
    ) {}

    public record ChecklistItemResponse(
            UUID id,
            String text,
            boolean completed,
            int position,
            Instant completedAt,
            UUID completedBy
    ) {}

    public record DependencyResponse(
            UUID id,
            UUID relatedTaskId,
            String relatedTaskKey,
            String relatedTaskTitle,
            String type,
            Instant createdAt
    ) {}

    public record WatcherResponse(
            UUID userId,
            Instant createdAt
    ) {}

    public record AttachmentResponse(
            UUID id,
            String fileName,
            long sizeBytes,
            String contentType,
            String storageUrl,
            String virusScanStatus,
            UUID uploadedBy,
            Instant uploadedAt
    ) {}

    public record TaskHistoryResponse(
            String fieldChanged,
            String oldValue,
            String newValue,
            UUID changedBy,
            String reason,
            Instant changedAt
    ) {}

    public record TaskActivityResponse(
            String activityType,
            UUID actorId,
            String summary,
            Instant occurredAt
    ) {}

    public record TaskMutationResponse(
            String message
    ) {}

    /** Board read model: tasks grouped by status column, so the frontend doesn't assemble a Kanban board from N separate list calls. */
    public record BoardResponse(
            UUID projectId,
            Map<String, List<TaskSummaryResponse>> columns
    ) {}

    /** Dashboard/analytics read model — computed live via indexed aggregate queries, not incrementally cached (see TaskService's javadoc for why this differs from Team's metrics design). */
    public record TaskAnalyticsResponse(
            UUID projectId,
            long tasksCreated,
            long tasksCompleted,
            double completionRatePercent,
            Double averageCycleTimeHours,
            Double averageLeadTimeHours,
            Map<String, Long> countsByStatus
    ) {}
}
