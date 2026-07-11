package com.saanjha.modules.notification.webhook;

import com.saanjha.modules.notification.entity.DeliveryStatus;
import com.saanjha.modules.notification.entity.NotificationDelivery;
import com.saanjha.modules.notification.entity.ProviderName;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies a verified {@link ProviderCallbackPayload} to the {@link
 * NotificationDelivery} it references. Deliberately idempotent and
 * monotonic: a callback can only move a delivery *forward*
 * (SENT -&gt; DELIVERED, anything -&gt; FAILED-then-retry-eligible), never
 * backward - a late-arriving or duplicate "delivered" callback for an
 * already-READ delivery is a no-op, not a regression of state the user has
 * already acted on.
 */
@Service
@RequiredArgsConstructor
public class ProviderCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCallbackService.class);

    private final NotificationDeliveryRepository deliveryRepository;

    @Transactional
    public void apply(ProviderCallbackPayload payload) {
        Optional<NotificationDelivery> maybeDelivery = deliveryRepository.findById(payload.deliveryId());
        if (maybeDelivery.isEmpty()) {
            log.warn("Received a delivery-status callback for unknown deliveryId={} - ignoring", payload.deliveryId());
            return;
        }
        NotificationDelivery delivery = maybeDelivery.get();

        if (isTerminalAndFinal(delivery.getStatus())) {
            log.debug("Ignoring callback for delivery {} already in terminal state {}", delivery.getId(), delivery.getStatus());
            return;
        }

        switch (payload.status()) {
            case "DELIVERED" -> delivery.markDelivered(ProviderName.NOTIFICATION_HUB);
            case "READ" -> delivery.markRead();
            case "FAILED" -> {
                // A provider-reported async failure after we already thought it was SENT -
                // let the normal retry scheduler's backoff logic decide what happens next,
                // rather than this callback handler reimplementing retry/backoff/DLQ policy.
                delivery.scheduleRetryOrExhaust(
                        "NotificationHub callback reported async delivery failure: " + payload.errorMessage(),
                        java.time.Instant.now());
            }
            default -> log.warn("Unrecognized callback status '{}' for delivery {} - ignoring", payload.status(), delivery.getId());
        }
        deliveryRepository.save(delivery);
    }

    private static boolean isTerminalAndFinal(DeliveryStatus status) {
        return status == DeliveryStatus.READ || status == DeliveryStatus.EXPIRED || status == DeliveryStatus.CANCELLED;
    }
}
