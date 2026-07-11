package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.NotificationDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeadLetterRepository extends JpaRepository<NotificationDeadLetter, java.util.UUID> {
    Page<NotificationDeadLetter> findByResolvedAtIsNullOrderByMovedAtDesc(Pageable pageable);
}
