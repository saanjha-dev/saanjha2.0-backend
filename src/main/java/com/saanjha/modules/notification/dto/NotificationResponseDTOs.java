package com.saanjha.modules.notification.dto;

import com.saanjha.modules.notification.entity.DeliveryMode;
import com.saanjha.modules.notification.entity.DeliveryStatus;
import com.saanjha.modules.notification.entity.NotificationCategory;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationPriority;
import com.saanjha.modules.notification.entity.ProviderName;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public final class NotificationResponseDTOs {

    private NotificationResponseDTOs() {
    }

    public record NotificationSummary(
            UUID id,
            String eventType,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String body,
            String actionUrl,
            DeliveryStatus status,
            boolean read,
            Instant createdAt
    ) {}

    public record PreferencesResponse(
            UUID userId,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean pushEnabled,
            boolean inAppEnabled,
            boolean webhookEnabled,
            boolean doNotDisturb,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String timezone,
            String locale,
            DeliveryMode defaultMode
    ) {}

    public record DeliveryDetail(
            UUID id,
            NotificationChannel channel,
            DeliveryStatus status,
            ProviderName lastProvider,
            int attemptCount,
            int maxAttempts,
            String lastError,
            Instant sentAt,
            Instant deliveredAt
    ) {}

    public record DeadLetterSummary(
            UUID id,
            UUID deliveryId,
            UUID notificationId,
            NotificationChannel channel,
            String reason,
            Instant movedAt,
            boolean resolved
    ) {}

    public record ProviderHealthSummary(
            String providerChannelKey,
            ProviderName provider,
            NotificationChannel channel,
            int consecutiveFailures,
            long totalAttempts,
            long totalFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt
    ) {}
}
