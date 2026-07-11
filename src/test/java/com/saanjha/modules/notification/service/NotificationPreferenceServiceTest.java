package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.repository.NotificationEventPreferenceRepository;
import com.saanjha.modules.notification.repository.NotificationPreferenceRepository;
import com.saanjha.modules.notification.rule.NotificationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private NotificationEventPreferenceRepository eventPreferenceRepository;

    private NotificationPreferenceService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferenceRepository, eventPreferenceRepository);
        userId = UUID.randomUUID();
        when(eventPreferenceRepository.findByUserIdAndEventType(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void noPreferenceRowYet_fallsBackToDefaults() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        NotificationRule rule = new NotificationRule(NotificationCategory.TEAM, NotificationPriority.NORMAL, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));

        var resolved = service.resolveDelivery(userId, "MEMBER_JOINED", rule);

        assertThat(resolved.suppressed()).isFalse();
        assertThat(resolved.channels()).contains(NotificationChannel.IN_APP, NotificationChannel.EMAIL); // defaults: email on, in_app on
    }

    @Test
    void doNotDisturb_suppressesNormalPriority() {
        NotificationPreference prefs = NotificationPreference.defaults(userId);
        prefs.setDoNotDisturb(true);
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));
        NotificationRule rule = new NotificationRule(NotificationCategory.TEAM, NotificationPriority.NORMAL, Set.of(NotificationChannel.IN_APP));

        assertThat(service.resolveDelivery(userId, "MEMBER_JOINED", rule).suppressed()).isTrue();
    }

    @Test
    void doNotDisturb_neverSuppressesCriticalPriority() {
        NotificationPreference prefs = NotificationPreference.defaults(userId);
        prefs.setDoNotDisturb(true);
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));
        NotificationRule rule = new NotificationRule(NotificationCategory.SECURITY, NotificationPriority.CRITICAL, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));

        var resolved = service.resolveDelivery(userId, "SUSPICIOUS_ACTIVITY_DETECTED", rule);

        assertThat(resolved.suppressed()).isFalse();
        assertThat(resolved.mode()).isEqualTo(DeliveryMode.INSTANT);
    }

    @Test
    void quietHours_deferNormalPriorityToDigestButKeepItEnabled() {
        NotificationPreference prefs = NotificationPreference.defaults(userId);
        LocalTime now = LocalTime.now();
        prefs.setQuietHoursStart(now.minusHours(1));
        prefs.setQuietHoursEnd(now.plusHours(1));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));
        NotificationRule rule = new NotificationRule(NotificationCategory.TEAM, NotificationPriority.NORMAL, Set.of(NotificationChannel.IN_APP));

        var resolved = service.resolveDelivery(userId, "MEMBER_JOINED", rule);

        assertThat(resolved.suppressed()).isFalse();
        assertThat(resolved.mode()).isEqualTo(DeliveryMode.DIGEST);
    }

    @Test
    void eventLevelMuteOverridesGlobalChannelEnablement() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(NotificationPreference.defaults(userId)));
        when(eventPreferenceRepository.findByUserIdAndEventType(userId, "TASK_ASSIGNED"))
                .thenReturn(Optional.of(NotificationEventPreference.of(userId, "TASK_ASSIGNED", false, null)));
        NotificationRule rule = new NotificationRule(NotificationCategory.TASK, NotificationPriority.NORMAL, Set.of(NotificationChannel.IN_APP));

        assertThat(service.resolveDelivery(userId, "TASK_ASSIGNED", rule).suppressed()).isTrue();
    }

    @Test
    void disablingAllChannelsForAnEvent_stillKeepsInAppFeedAlive() {
        NotificationPreference prefs = NotificationPreference.defaults(userId);
        prefs.setEmailEnabled(false);
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));
        NotificationRule rule = new NotificationRule(NotificationCategory.APPLICATION, NotificationPriority.NORMAL, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));

        var resolved = service.resolveDelivery(userId, "APPLICATION_SUBMITTED", rule);

        assertThat(resolved.suppressed()).isFalse();
        assertThat(resolved.channels()).containsExactly(NotificationChannel.IN_APP);
    }
}
