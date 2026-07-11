package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, java.util.UUID> {

    Optional<NotificationTemplate> findByEventTypeAndChannelAndLocaleAndActiveTrue(
            String eventType, NotificationChannel channel, String locale);
}
