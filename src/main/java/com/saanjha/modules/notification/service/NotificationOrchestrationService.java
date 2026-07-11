package com.saanjha.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.modules.notification.rule.NotificationEventType;
import com.saanjha.modules.notification.rule.NotificationRule;
import com.saanjha.modules.notification.rule.NotificationRuleRegistry;
import com.saanjha.modules.notification.template.TemplateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single entry point {@code NotificationEventListener} calls for every
 * domain event. This is the "outbox write" transaction described in {@link
 * NotificationDelivery}'s javadoc: by the time {@link #enqueue} returns, the
 * {@link Notification} and every eligible per-channel {@link
 * NotificationDelivery} row are durably committed - actual provider dispatch
 * (which can fail, retry, or degrade to CONSOLE) is a completely separate
 * concern handled later by {@code NotificationDispatchService}. Nothing in
 * this class ever calls a provider or the SDK.
 * <p>
 * {@code REQUIRES_NEW}: the calling event listener is itself
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, so the
 * publishing module's transaction has already committed by the time this
 * runs - there is nothing to join. A fresh transaction here means a failure
 * writing the Notification can never be blamed on, or roll back, unrelated
 * work; it's caught and logged by the listener's own {@code safely(...)}
 * wrapper, exactly like every other listener in this codebase.
 */
@Service
@RequiredArgsConstructor
public class NotificationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceService preferenceService;
    private final RecipientAddressResolver addressResolver;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    @Value("${notification.digest.default-delay-minutes:60}")
    private long digestDelayMinutes;

    public record EnqueueCommand(
            UUID recipientUserId,
            NotificationEventType eventType,
            String naturalKey,       // uniquely identifies the underlying fact, e.g. an applicationId or a "teamId:memberId" pair
            Map<String, Object> variables
    ) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(EnqueueCommand command) {
        String sourceEventId = command.eventType().name() + ":" + command.naturalKey();

        // Idempotency at the constraint level (this codebase's established convention -
        // see Team's duplicate-membership guard, Portfolio's badge engine). This existence
        // check plus the DB unique constraint together make double-enqueue (e.g. an
        // at-least-once event redelivery after this method partially ran and then crashed)
        // impossible rather than merely unlikely.
        Optional<Notification> existing = notificationRepository.findByRecipientUserIdAndSourceEventId(command.recipientUserId(), sourceEventId);
        if (existing.isPresent()) {
            log.debug("Notification already exists for recipient={} sourceEventId={} - skipping duplicate enqueue", command.recipientUserId(), sourceEventId);
            return;
        }

        NotificationRule rule = NotificationRuleRegistry.get(command.eventType());
        NotificationPreferenceService.ResolvedDelivery resolved = preferenceService.resolveDelivery(
                command.recipientUserId(), command.eventType().name(), rule);

        if (resolved.suppressed()) {
            log.debug("Notification suppressed by preferences for recipient={} eventType={}", command.recipientUserId(), command.eventType());
            return;
        }

        TemplateService.RenderedContent content = templateService.render(
                command.eventType().name(), pickPrimaryChannelForContent(resolved.channels()), resolved.locale(), command.variables());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(command.variables());
        } catch (Exception ex) {
            payloadJson = "{}";
        }

        Notification notification = Notification.create(
                command.recipientUserId(), command.eventType().name(), rule.category(), rule.priority(),
                content.subject() != null ? content.subject() : content.body(), content.body(), content.actionUrl(),
                sourceEventId, payloadJson);
        notification = notificationRepository.save(notification);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(rule.ttlFor());

        for (NotificationChannel channel : resolved.channels()) {
            Optional<String> address = addressResolver.resolve(command.recipientUserId(), channel);
            if (address.isEmpty()) {
                log.debug("Skipping channel={} for recipient={} eventType={} - no resolvable address (see RecipientAddressResolver)",
                        channel, command.recipientUserId(), command.eventType());
                continue;
            }

            Instant nextAttemptAt = resolved.mode() == DeliveryMode.DIGEST ? now.plusSeconds(digestDelayMinutes * 60) : now;

            NotificationDelivery delivery = NotificationDelivery.queue(
                    notification.getId(), channel, resolved.mode(), rule.maxAttemptsFor(), nextAttemptAt, expiresAt, address.get());
            deliveryRepository.save(delivery);
        }
    }

    /** Templates are per-channel, but Notification's own title/body summary (for the in-app feed and audit) needs exactly one rendering - prefer IN_APP's copy since it's plain text and always present when any channel fires. */
    private NotificationChannel pickPrimaryChannelForContent(java.util.Set<NotificationChannel> channels) {
        if (channels.contains(NotificationChannel.IN_APP)) return NotificationChannel.IN_APP;
        return channels.iterator().next();
    }
}
