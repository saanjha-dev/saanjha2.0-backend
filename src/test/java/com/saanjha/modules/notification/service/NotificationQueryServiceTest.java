package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.entity.Notification;
import com.saanjha.modules.notification.entity.NotificationCategory;
import com.saanjha.modules.notification.entity.NotificationPriority;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock private NotificationRepository notificationRepository;

    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(notificationRepository);
    }

    @Test
    void markRead_forSomeoneElsesNotification_returnsNotFoundNotForbidden() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.create(owner, "MEMBER_JOINED", NotificationCategory.TEAM,
                NotificationPriority.NORMAL, "t", "b", null, "MEMBER_JOINED:1", "{}");
        ReflectionTestUtils.setField(notification, "id", notificationId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead(attacker, notificationId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void markRead_forOwnNotification_succeedsAndSetsReadAt() {
        UUID owner = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.create(owner, "MEMBER_JOINED", NotificationCategory.TEAM,
                NotificationPriority.NORMAL, "t", "b", null, "MEMBER_JOINED:1", "{}");
        ReflectionTestUtils.setField(notification, "id", notificationId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        var summary = service.markRead(owner, notificationId);

        assertThat(summary.read()).isTrue();
    }
}
