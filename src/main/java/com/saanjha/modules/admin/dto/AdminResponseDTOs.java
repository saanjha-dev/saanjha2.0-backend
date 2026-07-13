package com.saanjha.modules.admin.dto;

import java.time.Instant;
import java.util.UUID;

public final class AdminResponseDTOs {

    private AdminResponseDTOs() {
    }

    public record ModerationActionResponse(
            UUID id, String targetType, UUID targetId, String actionType,
            UUID actorId, String reason, UUID relatedReportId, boolean reversed, Instant createdAt
    ) {}

    public record ReportResponse(
            UUID id, UUID reporterUserId, String targetType, UUID targetId, String category,
            String description, String status, UUID assignedModeratorId, String resolutionNotes,
            UUID resolvedBy, Instant resolvedAt, Instant createdAt
    ) {}

    public record AppealResponse(
            UUID id, UUID moderationActionId, UUID appellantUserId, String statement,
            String status, UUID decidedBy, String decisionNotes, Instant decidedAt, Instant createdAt
    ) {}

    public record FeatureFlagResponse(
            UUID id, String flagKey, String description, String flagType, boolean enabled,
            Integer rolloutPercentage, Instant updatedAt
    ) {}

    public record PlatformSettingResponse(UUID id, String settingKey, String settingValue, String valueType, String description, Instant updatedAt) {}

    public record AnnouncementResponse(
            UUID id, String title, String body, String type, String audience, String priority,
            String status, Instant startsAt, Instant expiresAt, Instant publishedAt, Instant createdAt
    ) {}

    public record AuditLogResponse(
            UUID id, UUID actorId, String action, String targetType, UUID targetId,
            String reason, String requestId, Instant occurredAt
    ) {}

    public record TrustScoreResponse(UUID userId, double score, String riskLevel, int reportCount, int upheldReportCount) {}

    public record DashboardOverviewResponse(
            long openReports, long inReviewReports, long pendingAppeals,
            long moderationActionsLast24h, long activeAnnouncements, long highRiskUsers
    ) {}

    public record AdminNoteResponse(UUID id, String targetType, UUID targetId, UUID authorId, String note, Instant createdAt) {}
}
