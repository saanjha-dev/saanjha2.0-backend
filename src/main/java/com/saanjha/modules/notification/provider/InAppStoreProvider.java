package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;
import org.springframework.stereotype.Component;

/**
 * IN_APP has no external transport: the {@code NotificationDelivery} row
 * itself, plus the parent {@code Notification}'s title/body, IS what GET
 * /v1/notifications serves. This provider exists only so IN_APP flows
 * through the exact same dispatch/retry/audit pipeline as every other
 * channel rather than being special-cased - "delivery" here just means
 * "confirm the row is persisted and ready to be read", which by the time
 * this provider is invoked it already is.
 */
@Component
public class InAppStoreProvider implements NotificationProvider {

    @Override
    public ProviderName name() {
        return ProviderName.IN_APP_STORE;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public ProviderDispatchResult send(ProviderDispatchRequest request) {
        return ProviderDispatchResult.delivered(200, request.deliveryId().toString());
    }
}
