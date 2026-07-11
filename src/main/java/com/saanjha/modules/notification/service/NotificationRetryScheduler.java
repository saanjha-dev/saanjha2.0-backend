package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.entity.NotificationDelivery;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The only place this module runs on a timer. Two responsibilities:
 * <ol>
 *   <li>{@link #sweepDueDeliveries} - the actual dispatch driver. Every QUEUED
 *       row (fresh enqueue or a DIGEST-deferred one whose window arrived) and
 *       every RETRYING row whose backoff has elapsed gets exactly one dispatch
 *       attempt per sweep, via {@link NotificationDeliveryRepository#findDueForDispatch}'s
 *       {@code FOR UPDATE SKIP LOCKED} query - this is what makes "dispatch
 *       immediately after enqueue" and "retry with backoff" and "release a
 *       DIGEST delivery on schedule" all the same code path.</li>
 *   <li>{@link #reclaimStuckProcessing} - a delivery can be left PROCESSING
 *       forever if the app crashes mid-{@code NotificationDispatchService.dispatch}
 *       between {@code beginProcessing()} and the terminal status write. This
 *       reclaims anything stuck past a threshold back to RETRYING so it's
 *       picked up again rather than silently wedging - the module brief's
 *       "must continue functioning even if [it] crashes" applies to itself
 *       crashing mid-dispatch, not only to the SDK/provider failing.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDispatchService dispatchService;

    @Value("${notification.dispatch.sweep-batch-size:200}")
    private int batchSize;

    @Value("${notification.dispatch.stuck-processing-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    @Scheduled(fixedDelayString = "${notification.dispatch.sweep-fixed-delay-ms:5000}")
    public void sweepDueDeliveries() {
        List<NotificationDelivery> due = findDue();
        if (due.isEmpty()) {
            return;
        }
        log.debug("Dispatch sweep picked up {} due delivery(ies)", due.size());
        for (NotificationDelivery delivery : due) {
            try {
                dispatchService.dispatch(delivery.getId());
            } catch (Exception ex) {
                // dispatch() already isolates provider failures into the delivery's own
                // state (REQUIRES_NEW + internal try/catch) - reaching here means something
                // outside that contract broke (e.g. a DB connectivity blip). Log and move on;
                // this row's next_attempt_at is unchanged so it's picked up again next sweep.
                log.error("Unexpected error dispatching delivery {} - will retry on the next sweep", delivery.getId(), ex);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected List<NotificationDelivery> findDue() {
        return deliveryRepository.findDueForDispatch(Instant.now(), batchSize);
    }

    @Scheduled(fixedDelayString = "#{${notification.dispatch.stuck-processing-threshold-minutes:10} * 60000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reclaimStuckProcessing() {
        Instant staleBefore = Instant.now().minusSeconds(stuckThresholdMinutes * 60);
        List<NotificationDelivery> stuck = deliveryRepository.findStuckInProcessing(staleBefore, batchSize);
        for (NotificationDelivery delivery : stuck) {
            log.warn("Reclaiming delivery {} stuck in PROCESSING since {} - almost certainly an app restart mid-dispatch", delivery.getId(), delivery.getUpdatedAt());
            delivery.scheduleRetryOrExhaust("Reclaimed from a stuck PROCESSING state (likely app restart mid-dispatch)", Instant.now());
        }
    }
}
