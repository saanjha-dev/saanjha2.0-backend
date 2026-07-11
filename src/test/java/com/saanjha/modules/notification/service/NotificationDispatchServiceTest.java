package com.saanjha.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.provider.*;
import com.saanjha.modules.notification.repository.NotificationDeadLetterRepository;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.modules.notification.repository.ProviderAttemptRepository;
import com.saanjha.modules.notification.template.TemplateService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ProviderAttemptRepository providerAttemptRepository;
    @Mock private NotificationDeadLetterRepository deadLetterRepository;
    @Mock private ProviderChainResolver providerChainResolver;
    @Mock private ProviderHealthTracker healthTracker;
    @Mock private TemplateService templateService;
    @Mock private NotificationProvider primaryProvider;
    @Mock private NotificationProvider fallbackProvider;

    private NotificationDispatchService dispatchService;
    private UUID deliveryId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        dispatchService = new NotificationDispatchService(
                deliveryRepository, notificationRepository, providerAttemptRepository, deadLetterRepository,
                providerChainResolver, healthTracker, templateService, new SimpleMeterRegistry(), new ObjectMapper());
        deliveryId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        lenient().when(templateService.render(any(), any(), any(), any())).thenReturn(new TemplateService.RenderedContent("s", "b", null));
    }

    private NotificationDelivery freshDelivery(int maxAttempts) {
        NotificationDelivery d = NotificationDelivery.queue(notificationId, NotificationChannel.EMAIL, DeliveryMode.INSTANT,
                maxAttempts, Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), "user@example.com");
        return d;
    }

    private Notification freshNotification() {
        return Notification.create(UUID.randomUUID(), "APPLICATION_SUBMITTED", NotificationCategory.APPLICATION,
                NotificationPriority.NORMAL, "t", "b", null, "APPLICATION_SUBMITTED:1", "{}");
    }

    @Test
    void firstProviderFails_secondProviderInChainSucceeds() throws Exception {
        NotificationDelivery delivery = freshDelivery(4);
        setId(delivery, deliveryId); // Back to your original helper

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        // FIX: Use lenient()...any() so Mockito doesn't crash if the entity returns a null ID
        lenient().when(deliveryRepository.findByNotificationId(any())).thenReturn(List.of(delivery));
        lenient().when(notificationRepository.findById(any())).thenReturn(Optional.of(freshNotification()));

        when(providerChainResolver.resolve(NotificationChannel.EMAIL)).thenReturn(List.of(primaryProvider, fallbackProvider));

        when(primaryProvider.name()).thenReturn(ProviderName.NOTIFICATION_HUB);
        when(primaryProvider.send(any())).thenThrow(new ProviderDispatchException("boom", false));
        when(fallbackProvider.name()).thenReturn(ProviderName.SMTP);
        when(fallbackProvider.send(any())).thenReturn(ProviderDispatchResult.accepted(250, null));

        dispatchService.dispatch(deliveryId);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getLastProvider()).isEqualTo(ProviderName.SMTP);
        verify(healthTracker).recordFailure(eq(ProviderName.NOTIFICATION_HUB), eq(NotificationChannel.EMAIL), any());
        verify(healthTracker).recordSuccess(ProviderName.SMTP, NotificationChannel.EMAIL);
        verify(providerAttemptRepository, times(2)).save(any());
    }

    @Test
    void everyProviderFailsAndAttemptsExhausted_movesToDeadLetter() throws Exception {
        NotificationDelivery delivery = freshDelivery(1);
        setId(delivery, deliveryId);

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        // FIX: Use lenient()...any() here too
        lenient().when(deliveryRepository.findByNotificationId(any())).thenReturn(List.of(delivery));
        lenient().when(notificationRepository.findById(any())).thenReturn(Optional.of(freshNotification()));

        when(providerChainResolver.resolve(NotificationChannel.EMAIL)).thenReturn(List.of(primaryProvider));
        when(primaryProvider.name()).thenReturn(ProviderName.NOTIFICATION_HUB);
        when(primaryProvider.send(any())).thenThrow(new ProviderDispatchException("still down", false));

        dispatchService.dispatch(deliveryId);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(deadLetterRepository).save(any());
    }

    @Test
    void expiredDelivery_isMarkedExpiredWithoutAttemptingDispatch() {
        NotificationDelivery delivery = NotificationDelivery.queue(notificationId, NotificationChannel.EMAIL, DeliveryMode.INSTANT,
                4, Instant.now().minusSeconds(10), Instant.now().minusSeconds(1), "user@example.com"); // already past expiresAt
        setId(delivery, deliveryId);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        dispatchService.dispatch(deliveryId);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXPIRED);
        verifyNoInteractions(providerChainResolver);
    }

    @Test
    void alreadyProcessedByAConcurrentSweep_isANoOp() {
        NotificationDelivery delivery = freshDelivery(4);
        delivery.markSent(ProviderName.SMTP); // already SENT by a racing sweep
        setId(delivery, deliveryId);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        dispatchService.dispatch(deliveryId);

        verifyNoInteractions(providerChainResolver);
    }

    private static void setId(NotificationDelivery delivery, UUID id) {
        org.springframework.test.util.ReflectionTestUtils.setField(delivery, "id", id);
    }
}
