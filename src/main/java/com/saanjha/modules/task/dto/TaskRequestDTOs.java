package com.saanjha.modules.task.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public class TaskRequestDTOs {

    private static final String TYPE_PATTERN = "^(FEATURE|BUG|CHORE|DOCUMENTATION|RESEARCH|DESIGN|INFRASTRUCTURE)$";
    private static final String PRIORITY_PATTERN = "^(LOW|MEDIUM|HIGH|URGENT|CRITICAL)$";
    private static final String DEPENDENCY_TYPE_PATTERN = "^(BLOCKS|BLOCKED_BY|DUPLICATE_OF|RELATES_TO|PARENT|CHILD)$";
    private static final String LABEL_SCOPE_PATTERN = "^(SYSTEM|PROJECT)$";

    public record CreateTaskRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200)
            String title,

            @Size(max = 10000)
            String description,

            @NotBlank(message = "Type is required")
            @Pattern(regexp = TYPE_PATTERN, message = "Invalid task type")
            String type,

            @Pattern(regexp = PRIORITY_PATTERN, message = "Invalid priority")
            String priority,

            UUID assigneeId,

            @Min(0) Integer storyPoints,

            @DecimalMin("0.0") Double estimatedHours,

            Instant dueDate
    ) {}

    public record UpdateTaskRequest(
            @Size(max = 200)
            String title,

            @Size(max = 10000)
            String description,

            @Pattern(regexp = TYPE_PATTERN, message = "Invalid task type")
            String type,

            @Pattern(regexp = PRIORITY_PATTERN, message = "Invalid priority")
            String priority,

            @Min(0) Integer storyPoints,

            @DecimalMin("0.0") Double estimatedHours,

            Instant dueDate
    ) {}

    public record MoveTaskRequest(
            @NotBlank(message = "Target status is required")
            String targetStatus,

            @Size(max = 500)
            String reason
    ) {}

    public record LogHoursRequest(
            @DecimalMin(value = "0.01", message = "Logged hours must be positive")
            double hours
    ) {}

    public record AssignTaskRequest(
            @NotNull(message = "Assignee id is required")
            UUID assigneeId
    ) {}

    public record AddChecklistItemRequest(
            @NotBlank(message = "Checklist item text is required")
            @Size(max = 500)
            String text
    ) {}

    public record ReorderChecklistItemRequest(
            @Min(0)
            int position
    ) {}

    public record AddLabelRequest(
            @NotBlank(message = "Label name is required")
            @Size(max = 50)
            String name,

            @Pattern(regexp = LABEL_SCOPE_PATTERN, message = "Invalid label scope")
            String scope
    ) {}

    public record AddDependencyRequest(
            @NotNull(message = "Related task id is required")
            UUID relatedTaskId,

            @NotBlank(message = "Dependency type is required")
            @Pattern(regexp = DEPENDENCY_TYPE_PATTERN, message = "Invalid dependency type")
            String type
    ) {}

    public record AddAttachmentRequest(
            @NotBlank @Size(max = 255) String fileName,
            @Min(1) long sizeBytes,
            @NotBlank @Size(max = 100) String contentType,
            @NotBlank @Size(max = 1000) String storageUrl,
            @NotBlank @Size(max = 128) String checksum
    ) {}

    public record TaskSearchCriteria(
            String status,
            String priority,
            UUID assigneeId,
            String label,
            UUID createdBy,
            Instant createdAfter,
            Instant createdBefore,
            String keyword
    ) {}
}
