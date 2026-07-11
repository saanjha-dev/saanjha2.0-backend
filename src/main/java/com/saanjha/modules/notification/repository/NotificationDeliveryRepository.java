package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationId(UUID notificationId);

    /**
     * The dispatch scanner's one query. {@code FOR UPDATE SKIP LOCKED} is the
     * standard Postgres pattern for "many workers can safely poll the same
     * queue table" - if this app ever runs more than one instance, two
     * schedulers hitting this at once simply split the work instead of
     * double-sending. Bounded by {@code limit} so one sweep can't starve the
     * connection pool on a large backlog.
     */
    @Query(value = """
            SELECT * FROM ntf.ntf_deliveries
            WHERE status IN ('QUEUED', 'RETRYING')
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> findDueForDispatch(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM ntf.ntf_deliveries
            WHERE status = 'PROCESSING'
              AND updated_at < :staleBefore
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> findStuckInProcessing(@Param("staleBefore") Instant staleBefore, @Param("limit") int limit);
}
