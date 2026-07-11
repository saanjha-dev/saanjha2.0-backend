package com.saanjha.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.modules.notification.rule.NotificationEventType;
import com.saanjha.modules.notification.service.NotificationOrchestrationService.EnqueueCommand;
import com.saanjha.modules.notification.template.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestrationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private RecipientAddressResolver addressResolver;
    @Mock private TemplateService templateService;

    private NotificationOrchestrationService orchestrationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orchestrationService = new NotificationOrchestrationService(
                notificationRepository, deliveryRepository, preferenceService, addressResolver,
                templateService, new ObjectMapper());
        ReflectionTestUtils.setField(orchestrationService, "digestDelayMinutes", 60L);
        userId = UUID.randomUUID();
    }

    @Test
    void duplicateSourceEventId_isSkippedEntirely() {
        when(notificationRepository.findByRecipientUserIdAndSourceEventId(any(), any()))
                .thenReturn(Optional.of(Notification.create(userId, "X", NotificationCategory.TEAM, NotificationPriority.LOW, "t", "b", null, "X:1", "{}")));

        orchestrationService.enqueue(new EnqueueCommand(userId, NotificationEventType.MEMBER_JOINED, "1", Map.of()));

        verifyNoInteractions(preferenceService);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void suppressedByPreferences_createsNoNotificationOrDelivery() {
        when(notificationRepository.findByRecipientUserIdAndSourceEventId(any(), any())).thenReturn(Optional.empty());

        // FIX: Manually instantiate the Record with 'true' for the suppressed flag
        when(preferenceService.resolveDelivery(any(), any(), any())).thenReturn(
                new NotificationPreferenceService.ResolvedDelivery(true, Set.of(), DeliveryMode.INSTANT, "en"));

        orchestrationService.enqueue(new EnqueueCommand(userId, NotificationEventType.TASK_ASSIGNED, "task-1", Map.of()));

        verify(notificationRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void channelWithNoResolvableAddress_isSkippedButOthersStillDispatch() {
        when(notificationRepository.findByRecipientUserIdAndSourceEventId(any(), any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceService.resolveDelivery(any(), any(), any())).thenReturn(
                new NotificationPreferenceService.ResolvedDelivery(false, Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH), DeliveryMode.INSTANT, "en"));
        when(templateService.render(any(), any(), any(), any())).thenReturn(new TemplateService.RenderedContent("t", "b", null));
        when(addressResolver.resolve(userId, NotificationChannel.IN_APP)).thenReturn(Optional.of(userId.toString()));
        when(addressResolver.resolve(userId, NotificationChannel.PUSH)).thenReturn(Optional.empty()); // no device token on file

        orchestrationService.enqueue(new EnqueueCommand(userId, NotificationEventType.TASK_ASSIGNED, "task-1", Map.of()));

        verify(deliveryRepository, times(1)).save(argThat(d -> d.getChannel() == NotificationChannel.IN_APP));
        verify(deliveryRepository, never()).save(argThat(d -> d.getChannel() == NotificationChannel.PUSH));
    }

    @Test
    void digestMode_delaysNextAttemptIntoTheFuture() {
        when(notificationRepository.findByRecipientUserIdAndSourceEventId(any(), any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceService.resolveDelivery(any(), any(), any())).thenReturn(
                new NotificationPreferenceService.ResolvedDelivery(false, Set.of(NotificationChannel.IN_APP), DeliveryMode.DIGEST, "en"));
        when(templateService.render(any(), any(), any(), any())).thenReturn(new TemplateService.RenderedContent("t", "b", null));
        when(addressResolver.resolve(any(), any())).thenReturn(Optional.of(userId.toString()));

        orchestrationService.enqueue(new EnqueueCommand(userId, NotificationEventType.MEMBER_JOINED, "m-1", Map.of()));

        verify(deliveryRepository).save(argThat(d -> d.getNextAttemptAt().isAfter(java.time.Instant.now().plusSeconds(3000))));
    }
}
