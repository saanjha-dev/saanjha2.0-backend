package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.NotificationEventPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventPreferenceRepository extends JpaRepository<NotificationEventPreference, UUID> {
    Optional<NotificationEventPreference> findByUserIdAndEventType(UUID userId, String eventType);
    List<NotificationEventPreference> findByUserId(UUID userId);
}
