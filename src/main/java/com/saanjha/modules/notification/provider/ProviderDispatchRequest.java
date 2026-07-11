package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationPriority;

import java.util.UUID;

/**
 * Everything a provider needs to attempt one delivery - already fully
 * rendered by {@code TemplateService} before this is built, so no provider
 * implementation ever touches template variables or the raw event payload.
 */
public record ProviderDispatchRequest(
        UUID deliveryId,
        UUID notificationId,
        NotificationChannel channel,
        NotificationPriority priority,
        String recipientAddress, // email address / phone / device token / webhook URL / the recipient userId string for IN_APP
        String subject,          // rendered subject; null for channels without one
        String body,             // rendered body
        String actionUrl         // rendered deep link; may be null
) {}
