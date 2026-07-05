package com.saanjha.modules.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApplicationResponseDTOs {

    public record ApplicationResponse(
            UUID id,
            UUID projectId,
            UUID applicantId,
            String status,
            String message,
            String preferredRole,
            Integer weeklyHours,
            String timezone,
            Instant reviewedAt,
            UUID reviewedBy,
            String decisionReason,
            Instant withdrawnAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ApplicationSummaryResponse(
            UUID id,
            UUID projectId,
            UUID applicantId,
            String status,
            Instant createdAt
    ) {}

    public record ApplicationNoteResponse(
            UUID id,
            UUID authorId,
            String note,
            Instant createdAt
    ) {}

    public record ApplicationStatusLogResponse(
            String fromStatus,
            String toStatus,
            UUID changedBy,
            String reason,
            Instant changedAt
    ) {}

    /** Simple owner-dashboard aggregate: counts of applications by status for one project. */
    public record ApplicationStatsResponse(
            UUID projectId,
            Map<String, Long> countsByStatus,
            long totalApplications
    ) {}

    public record BulkReviewResultResponse(
            List<UUID> succeeded,
            Map<String, String> failed
    ) {}
}
