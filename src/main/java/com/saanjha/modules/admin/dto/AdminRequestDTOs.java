package com.saanjha.modules.admin.dto;

import com.saanjha.modules.admin.entity.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminRequestDTOs {

    private AdminRequestDTOs() {
    }

    // ---- User moderation ----
    public record WarnUserRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record SuspendUserRequest(@NotBlank @Size(max = 1000) String reason, Instant expiresAt) {}

    public record BanUserRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record RoleChangeRequest(@NotBlank String roleName, @NotBlank @Size(max = 500) String reason) {}

    // ---- Project moderation ----
    public record ProjectModerationRequest(@NotBlank @Size(max = 1000) String reason) {}

    // ---- Team moderation (thin passthrough to Team module) ----
    public record TeamModerationRequest(@NotBlank @Size(max = 1000) String reason) {}

    // ---- Reports ----
    public record SubmitReportRequest(
            @NotNull ModerationTargetType targetType,
            @NotNull UUID targetId,
            @NotNull ReportCategory category,
            @Size(max = 2000) String description
    ) {}

    public record AssignReportRequest(@NotNull UUID moderatorId) {}

    public record ResolveReportRequest(
            @NotNull ReportStatus resolution, // RESOLVED or DISMISSED
            @Size(max = 2000) String resolutionNotes
    ) {}

    // ---- Appeals ----
    public record SubmitAppealRequest(@NotNull UUID moderationActionId, @NotBlank @Size(max = 2000) String statement) {}

    public record DecideAppealRequest(@NotNull boolean grant, @Size(max = 2000) String decisionNotes) {}

    // ---- Admin notes ----
    public record CreateNoteRequest(@NotNull ModerationTargetType targetType, @NotNull UUID targetId, @NotBlank @Size(max = 2000) String note) {}

    // ---- Feature flags ----
    public record CreateFeatureFlagRequest(
            @NotBlank @Size(max = 150) String flagKey,
            @Size(max = 500) String description,
            @NotNull FeatureFlagType flagType,
            boolean enabled,
            Integer rolloutPercentage,
            List<UUID> targetUserIds,
            List<UUID> targetProjectIds
    ) {}

    public record UpdateFeatureFlagRequest(
            Boolean enabled,
            Integer rolloutPercentage,
            List<UUID> targetUserIds,
            List<UUID> targetProjectIds
    ) {}

    // ---- Platform settings ----
    public record UpdateSettingRequest(@NotBlank String settingValue, @NotNull PlatformSettingValueType valueType, String description) {}

    // ---- Announcements ----
    public record CreateAnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String body,
            @NotNull AnnouncementType type,
            @NotNull AnnouncementAudience audience,
            @NotNull AnnouncementPriority priority,
            Instant startsAt,
            Instant expiresAt
    ) {}
}
