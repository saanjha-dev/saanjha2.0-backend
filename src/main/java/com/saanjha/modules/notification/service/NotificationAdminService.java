package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.dto.NotificationResponseDTOs.DeadLetterSummary;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.ProviderHealthSummary;
import com.saanjha.modules.notification.entity.DeliveryMode;
import com.saanjha.modules.notification.entity.NotificationDeadLetter;
import com.saanjha.modules.notification.entity.NotificationDelivery;
import com.saanjha.modules.notification.repository.NotificationDeadLetterRepository;
import com.saanjha.modules.notification.repository.NotificationDeliveryRepository;
import com.saanjha.modules.notification.repository.ProviderHealthRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backs the {@code notification:admin}-gated operational endpoints - dead
 * letter visibility/requeue and provider health. Deliberately separate from
 * every user-facing service in this module (see V20's permission-seed
 * comment on why {@code notification:admin} is its own permission).
 */
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationDeadLetterRepository deadLetterRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final ProviderHealthRepository providerHealthRepository;

    @Transactional(readOnly = true)
    public Page<DeadLetterSummary> listDeadLetters(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return deadLetterRepository.findByResolvedAtIsNullOrderByMovedAtDesc(PageRequest.of(Math.max(page, 0), safeSize))
                .map(dl -> new DeadLetterSummary(dl.getId(), dl.getDeliveryId(), dl.getNotificationId(), dl.getChannel(),
                        dl.getReason(), dl.getMovedAt(), dl.getResolvedAt() != null));
    }

    /**
     * Requeue creates a brand-new {@link NotificationDelivery} row against
     * the original notification, rather than resurrecting the exhausted one
     * - the dead-letter row stays an honest, immutable historical record
     * (see {@code NotificationDeadLetter}'s javadoc), and the new delivery
     * gets a fresh attempt budget rather than inheriting an already-exhausted one.
     */
    @Transactional
    public void resolveDeadLetter(UUID deadLetterId, UUID resolvedByUserId, String note, boolean requeue) {
        NotificationDeadLetter deadLetter = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Dead letter entry not found"));
        if (deadLetter.getResolvedAt() != null) {
            throw new AppException(ErrorCode.CONFLICT, "This dead letter entry was already resolved");
        }

        if (requeue) {
            NotificationDelivery original = deliveryRepository.findById(deadLetter.getDeliveryId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Original delivery not found"));
            NotificationDelivery requeued = NotificationDelivery.queue(
                    deadLetter.getNotificationId(), deadLetter.getChannel(), DeliveryMode.INSTANT,
                    Math.max(original.getMaxAttempts(), 1), Instant.now(), Instant.now().plus(java.time.Duration.ofDays(3)),
                    original.getRecipientAddress());
            deliveryRepository.save(requeued);
        }

        deadLetter.resolve(resolvedByUserId, note);
        deadLetterRepository.save(deadLetter);
    }

    @Transactional(readOnly = true)
    public List<ProviderHealthSummary> providerHealth() {
        return providerHealthRepository.findAllByOrderByChannelAscConsecutiveFailuresAsc().stream()
                .map(h -> new ProviderHealthSummary(h.getProviderChannelKey(), h.getProvider(), h.getChannel(),
                        h.getConsecutiveFailures(), h.getTotalAttempts(), h.getTotalFailures(), h.getLastSuccessAt(), h.getLastFailureAt()))
                .toList();
    }
}
