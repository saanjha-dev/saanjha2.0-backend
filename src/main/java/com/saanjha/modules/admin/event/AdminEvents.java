package com.saanjha.modules.admin.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain events published by the Admin module. All payloads are flat value
 * records (UUID/String/Instant only), consistent with every other module's
 * event-payload constraint in this codebase (see Team/Project/Application's
 * own event classes).
 *
 * Admin publishes only meaningful governance events — never a raw CRUD echo
 * of a config-table write (per the Admin brief's "never publish CRUD events"
 * rule). {@code FeatureFlagChangedEvent}/{@code PlatformSettingChangedEvent}
 * are the one apparent exception: they look like CRUD echoes but are not —
 * they are the mechanism by which flags/settings become *live* elsewhere in
 * the system without every module polling Admin's tables, so the event
 * itself is the meaningful business fact ("this flag's effective value just
 * changed"), not an audit trail of the write.
 */
public final class AdminEvents {

    private AdminEvents() {
    }

    // ------------------------------------------------------------------
    // User moderation
    // ------------------------------------------------------------------

    public record UserWarnedEvent(UUID userId, UUID actorId, String reason, Instant occurredAt) {}

    public record UserSuspendedEvent(UUID userId, UUID actorId, String reason, Instant expiresAt, Instant occurredAt) {}

    public record UserReinstatedEvent(UUID userId, UUID actorId, Instant occurredAt) {}

    public record UserBannedEvent(UUID userId, UUID actorId, String reason, Instant occurredAt) {}

    public record UserShadowBannedEvent(UUID userId, UUID actorId, String reason, Instant occurredAt) {}

    public record UserRoleChangedEvent(UUID userId, UUID actorId, String roleName, boolean granted, Instant occurredAt) {}

    // ------------------------------------------------------------------
    // Project moderation
    // ------------------------------------------------------------------

    public record ProjectLockedEvent(UUID projectId, UUID actorId, String reason, Instant occurredAt) {}

    public record ProjectUnlockedEvent(UUID projectId, UUID actorId, Instant occurredAt) {}

    public record ProjectHiddenEvent(UUID projectId, UUID actorId, String reason, Instant occurredAt) {}

    public record ProjectUnhiddenEvent(UUID projectId, UUID actorId, Instant occurredAt) {}

    public record ProjectFeaturedEvent(UUID projectId, UUID actorId, Instant occurredAt) {}

    public record ProjectUnfeaturedEvent(UUID projectId, UUID actorId, Instant occurredAt) {}

    public record ProjectRemovedByAdminEvent(UUID projectId, UUID actorId, String reason, Instant occurredAt) {}

    // ------------------------------------------------------------------
    // Team moderation (thin wrapper confirmations — Team publishes its own
    // TeamLockedEvent/TeamDissolvedEvent already; these are Admin's mirror
    // for its unified audit timeline, not a duplicate authority)
    // ------------------------------------------------------------------

    public record TeamModerationActionRecordedEvent(UUID teamId, UUID actorId, String actionType, String reason, Instant occurredAt) {}

    // ------------------------------------------------------------------
    // Content moderation / Trust & Safety
    // ------------------------------------------------------------------

    public record ReportSubmittedEvent(UUID reportId, UUID reporterUserId, String targetType, UUID targetId, String category, Instant occurredAt) {}

    public record ReportResolvedEvent(UUID reportId, UUID resolvedBy, String resolution, Instant occurredAt) {}

    public record AppealDecidedEvent(UUID appealId, UUID moderationActionId, UUID appellantUserId, boolean granted, Instant occurredAt) {}

    public record TrustScoreDegradedEvent(UUID userId, double newScore, String riskLevel, Instant occurredAt) {}

    // ------------------------------------------------------------------
    // Feature flags & platform configuration
    // ------------------------------------------------------------------

    public record FeatureFlagChangedEvent(String flagKey, boolean enabled, UUID actorId, Instant occurredAt) {}

    public record PlatformConfigurationChangedEvent(String settingKey, String newValue, UUID actorId, Instant occurredAt) {}

    // ------------------------------------------------------------------
    // Announcements / system-wide operational state
    // ------------------------------------------------------------------

    public record AnnouncementPublishedEvent(UUID announcementId, String title, String audience, String priority, Instant occurredAt) {}

    public record AnnouncementExpiredEvent(UUID announcementId, Instant occurredAt) {}

    public record SystemMaintenanceStartedEvent(UUID actorId, String reason, Instant estimatedEndAt, Instant occurredAt) {}

    public record SystemMaintenanceEndedEvent(UUID actorId, Instant occurredAt) {}
}
