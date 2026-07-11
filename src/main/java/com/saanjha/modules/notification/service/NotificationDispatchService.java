package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.provider.*;
import com.saanjha.modules.notification.repository.NotificationDeadLetterRepository;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.modules.notification.repository.ProviderAttemptRepository;
import com.saanjha.modules.notification.template.TemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Attempts exactly one {@link NotificationDelivery} through its channel's
 * ordered provider chain (module brief's "Automatic Failover"): tries each
 * provider in order until one succeeds, records every attempt (success or
 * failure) as an audited {@link ProviderAttempt}, and updates the
 * delivery's/notification's status. Never lets a provider exception escape
 * to its caller ({@code NotificationRetryScheduler}) - a bad provider call
 * degrades this one delivery's own state, never the scheduler's sweep loop
 * (module brief's core architectural requirement).
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final long[] BACKOFF_SECONDS = {30, 120, 600, 1800, 3600}; // 30s, 2m, 10m, 30m, 1h, then capped

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final ProviderAttemptRepository providerAttemptRepository;
    private final NotificationDeadLetterRepository deadLetterRepository;
    private final ProviderChainResolver providerChainResolver;
    private final ProviderHealthTracker healthTracker;
    private final TemplateService templateService;
    private final MeterRegistry meterRegistry;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Runs in its own transaction per delivery (called once per row from the
     * scheduler's sweep) so one delivery's failure/exception can never roll
     * back another delivery processed in the same sweep batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return; // Raced with something else (shouldn't happen given SKIP LOCKED, but never worth throwing over).
        }
        if (delivery.getStatus() != DeliveryStatus.QUEUED && delivery.getStatus() != DeliveryStatus.RETRYING) {
            return; // Already handled by a concurrent sweep or since re-checked.
        }
        if (delivery.getExpiresAt().isBefore(Instant.now())) {
            delivery.expire();
            meterRegistry.counter("notification.delivery.expired", "channel", delivery.getChannel().name()).increment();
            return;
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
        if (notification == null) {
            log.warn("Delivery {} references missing notification {} - cancelling", delivery.getId(), delivery.getNotificationId());
            delivery.cancel();
            return;
        }

        delivery.beginProcessing();

        Map<String, Object> variables = readVariables(notification.getPayloadJson());
        // NOTE: hardcoded "en" rather than the recipient's actual NotificationPreference.locale -
        // Notification/NotificationDelivery don't persist locale at enqueue time, and re-fetching
        // preferences here just for a locale string was judged not worth another repository call
        // on the hot dispatch path for a platform that is English-only today anyway. If/when
        // localization becomes real, thread locale through EnqueueCommand -> Notification (a new
        // column, not a repeat preference lookup at dispatch time) rather than fixing it here -
        // see the module's Future Extension Points.
        TemplateService.RenderedContent content = templateService.render(
                notification.getEventType(), delivery.getChannel(), "en", variables);

        List<NotificationProvider> chain = providerChainResolver.resolve(delivery.getChannel());
        if (chain.isEmpty()) {
            log.error("No provider registered for channel {} - this is a configuration gap, not a runtime failure", delivery.getChannel());
            handleExhausted(delivery, notification, "No provider registered for channel " + delivery.getChannel());
            return;
        }

        ProviderDispatchRequest request = new ProviderDispatchRequest(
                delivery.getId(), notification.getId(), delivery.getChannel(), notification.getPriority(),
                delivery.getRecipientAddress(), content.subject(), content.body(),
                content.actionUrl() != null ? content.actionUrl() : notification.getActionUrl());

        for (NotificationProvider provider : chain) {
            long start = System.nanoTime();
            try {
                ProviderDispatchResult result = provider.send(request);
                long latencyMs = (System.nanoTime() - start) / 1_000_000;

                providerAttemptRepository.save(ProviderAttempt.record(
                        delivery.getId(), provider.name(), delivery.getAttemptCount(), true,
                        result.providerStatusCode(), null, latencyMs));
                healthTracker.recordSuccess(provider.name(), delivery.getChannel());
                recordDispatchMetric(delivery.getChannel(), provider.name(), true, latencyMs);

                if (result.delivered()) {
                    delivery.markDelivered(provider.name());
                } else {
                    delivery.markSent(provider.name());
                }
                recomputeNotificationStatus(notification);
                return;

            } catch (Exception ex) {
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

                providerAttemptRepository.save(ProviderAttempt.record(
                        delivery.getId(), provider.name(), delivery.getAttemptCount(), false, null, error, latencyMs));
                healthTracker.recordFailure(provider.name(), delivery.getChannel(), error);
                recordDispatchMetric(delivery.getChannel(), provider.name(), false, latencyMs);

                log.warn("Provider {} failed for delivery {} channel {}: {} - trying next provider in chain",
                        provider.name(), delivery.getId(), delivery.getChannel(), error);
                // Falls through to the next provider in the chain, per-channel automatic failover.
            }
        }

        // Every provider in the chain failed this attempt (CONSOLE, the last entry,
        // never throws - so reaching here in practice means even CONSOLE's own logging
        // call threw, which would indicate a deeper application problem, not a provider outage).
        Instant nextAttempt = Instant.now().plusSeconds(backoffSecondsFor(delivery.getAttemptCount()));
        boolean willRetry = delivery.scheduleRetryOrExhaust("Every provider in the chain failed this attempt", nextAttempt);
        if (!willRetry) {
            handleExhausted(delivery, notification, "Exhausted all " + delivery.getMaxAttempts() + " attempts across the full provider chain");
        }
        recomputeNotificationStatus(notification);
    }

    private void handleExhausted(NotificationDelivery delivery, Notification notification, String reason) {
        delivery.scheduleRetryOrExhaust(reason, Instant.now()); // Ensures status is FAILED even if attemptCount hasn't technically hit max yet (config-gap path).
        NotificationDeadLetter deadLetter = NotificationDeadLetter.of(
                delivery.getId(), notification.getId(), delivery.getChannel(), reason, notification.getPayloadJson());
        deadLetterRepository.save(deadLetter);
        meterRegistry.counter("notification.delivery.dead_letter", "channel", delivery.getChannel().name()).increment();
        recomputeNotificationStatus(notification);
    }

    /** Best status across this notification's deliveries - DELIVERED/SENT beats RETRYING beats FAILED, so one channel succeeding is reflected even if another is still struggling. */
    private void recomputeNotificationStatus(Notification notification) {
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notification.getId());
        DeliveryStatus best = deliveries.stream()
                .map(NotificationDelivery::getStatus)
                .min((a, b) -> Integer.compare(rank(a), rank(b)))
                .orElse(notification.getStatus());
        notification.setStatus(best);
        notificationRepository.save(notification);
    }

    private static int rank(DeliveryStatus status) {
        return switch (status) {
            case DELIVERED -> 0;
            case READ -> 0;
            case SENT -> 1;
            case PROCESSING -> 2;
            case QUEUED -> 3;
            case RETRYING -> 4;
            case FAILED -> 5;
            case EXPIRED -> 6;
            case CANCELLED -> 7;
            case CREATED -> 8;
        };
    }

    private static long backoffSecondsFor(int attemptNumber) {
        int idx = Math.min(Math.max(attemptNumber - 1, 0), BACKOFF_SECONDS.length - 1);
        return BACKOFF_SECONDS[idx];
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readVariables(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void recordDispatchMetric(NotificationChannel channel, ProviderName provider, boolean success, long latencyMs) {
        meterRegistry.counter("notification.dispatch.attempts", "channel", channel.name(), "provider", provider.name(), "success", String.valueOf(success)).increment();
        Timer.builder("notification.dispatch.latency")
                .tag("channel", channel.name())
                .tag("provider", provider.name())
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
