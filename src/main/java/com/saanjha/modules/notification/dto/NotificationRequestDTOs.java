package com.saanjha.modules.notification.dto;

import com.saanjha.modules.notification.entity.DeliveryMode;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public final class NotificationRequestDTOs {

    private NotificationRequestDTOs() {
    }

    /** Every field optional/nullable - PATCH semantics, only supplied fields are changed. */
    public record UpdatePreferencesRequest(
            Boolean emailEnabled,
            Boolean smsEnabled,
            Boolean pushEnabled,
            Boolean inAppEnabled,
            Boolean webhookEnabled,
            Boolean doNotDisturb,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            @Size(max = 50) String timezone,
            @Size(max = 10) String locale,
            DeliveryMode defaultMode
    ) {}

    public record SetEventPreferenceRequest(
            boolean enabled,
            DeliveryMode mode
    ) {}

    public record ResolveDeadLetterRequest(
            @Size(max = 500) String note,
            boolean requeue
    ) {}
}
