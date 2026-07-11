package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.dto.NotificationResponseDTOs.NotificationSummary;
import com.saanjha.modules.notification.entity.Notification;
import com.saanjha.modules.notification.repository.NotificationRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public record ListResult(Page<NotificationSummary> page, long unreadCount) {}

    @Transactional(readOnly = true)
    public ListResult list(UUID userId, int page, int size, boolean unreadOnly) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);

        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(userId);
        return new ListResult(result.map(this::toSummary), unreadCount);
    }

    @Transactional
    public NotificationSummary markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Notification not found"));
        if (!notification.getRecipientUserId().equals(userId)) {
            // Deliberately NOT_FOUND rather than FORBIDDEN - this codebase's convention elsewhere
            // (see architecture-review.md's IDOR findings) is to avoid confirming a resource
            // exists at all to a caller who isn't its owner.
            throw new AppException(ErrorCode.NOT_FOUND, "Notification not found");
        }
        notification.markRead();
        notificationRepository.save(notification);
        return toSummary(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        var page = notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, 500));
        page.getContent().forEach(Notification::markRead);
        notificationRepository.saveAll(page.getContent());
        return page.getContent().size();
    }

    private NotificationSummary toSummary(Notification n) {
        return new NotificationSummary(n.getId(), n.getEventType(), n.getCategory(), n.getPriority(),
                n.getTitle(), n.getBody(), n.getActionUrl(), n.getStatus(), n.getReadAt() != null, n.getCreatedAt());
    }
}
