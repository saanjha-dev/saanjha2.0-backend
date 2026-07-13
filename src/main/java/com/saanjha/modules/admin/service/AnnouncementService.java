package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.*;
import com.saanjha.modules.admin.event.AdminEvents.AnnouncementExpiredEvent;
import com.saanjha.modules.admin.event.AdminEvents.AnnouncementPublishedEvent;
import com.saanjha.modules.admin.repository.AnnouncementRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform Announcements: Banner/Maintenance/Emergency Broadcast, scheduled
 * or immediate, with expiration, priority, and audience targeting (Admin
 * brief, PLATFORM ANNOUNCEMENTS section). Publishing here does not itself
 * deliver anything through Notification — {@code AnnouncementPublishedEvent}
 * is the hook Notification would consume once it has an announcement
 * template (see Future Extension Points); today, the client-facing surface
 * is {@link #getLiveAnnouncements}, a direct read Discovery/Host-shell can
 * poll to render a banner.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Announcement create(UUID actorId, String title, String body, AnnouncementType type,
                                AnnouncementAudience audience, AnnouncementPriority priority,
                                Instant startsAt, Instant expiresAt) {
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setBody(body);
        announcement.setType(type);
        announcement.setAudience(audience);
        announcement.setPriority(priority);
        announcement.setStartsAt(startsAt);
        announcement.setExpiresAt(expiresAt);
        announcement.setCreatedBy(actorId);
        announcement.setStatus(startsAt == null || !startsAt.isAfter(Instant.now()) ? AnnouncementStatus.PUBLISHED : AnnouncementStatus.SCHEDULED);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            announcement.setPublishedAt(Instant.now());
        }
        announcement = announcementRepository.save(announcement);

        auditService.record(actorId, "ANNOUNCEMENT_CREATED", null, null, null, title, null);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            eventPublisher.publishEvent(new AnnouncementPublishedEvent(announcement.getId(), title, audience.name(), priority.name(), Instant.now()));
        }
        return announcement;
    }

    @Transactional
    public void cancel(UUID actorId, UUID announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Announcement not found."));
        if (announcement.getStatus() != AnnouncementStatus.DRAFT && announcement.getStatus() != AnnouncementStatus.SCHEDULED) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "Only draft or scheduled announcements can be cancelled — unpublish a live one instead.");
        }
        announcement.setStatus(AnnouncementStatus.CANCELLED);
        announcementRepository.save(announcement);
        auditService.record(actorId, "ANNOUNCEMENT_CANCELLED", null, null, null, null, null);
    }

    @Transactional
    public void unpublish(UUID actorId, UUID announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Announcement not found."));
        announcement.setStatus(AnnouncementStatus.EXPIRED);
        announcement.setExpiresAt(Instant.now());
        announcementRepository.save(announcement);
        auditService.record(actorId, "ANNOUNCEMENT_UNPUBLISHED", null, null, "PUBLISHED", "EXPIRED", null);
        eventPublisher.publishEvent(new AnnouncementExpiredEvent(announcementId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public Page<Announcement> listAll(Pageable pageable) {
        return announcementRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<Announcement> getLiveAnnouncements() {
        return announcementRepository.findByStatusAndExpiresAtAfterOrExpiresAtIsNull(AnnouncementStatus.PUBLISHED, Instant.now());
    }

    /**
     * Scheduled sweep: promotes SCHEDULED -> PUBLISHED once {@code startsAt}
     * has passed, and PUBLISHED -> EXPIRED once {@code expiresAt} has passed
     * — mirroring the exact "scheduled job transitions state" pattern
     * {@code ProjectGhostingSchedulerService} already established for
     * Project's ghosting sweep.
     */
    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void sweepScheduledAndExpired() {
        Instant now = Instant.now();
        for (Announcement announcement : announcementRepository.findByStatusAndStartsAtBefore(AnnouncementStatus.SCHEDULED, now)) {
            announcement.setStatus(AnnouncementStatus.PUBLISHED);
            announcement.setPublishedAt(now);
            announcementRepository.save(announcement);
            eventPublisher.publishEvent(new AnnouncementPublishedEvent(
                    announcement.getId(), announcement.getTitle(), announcement.getAudience().name(), announcement.getPriority().name(), now));
        }
        for (Announcement announcement : announcementRepository.findByStatusAndExpiresAtBefore(AnnouncementStatus.PUBLISHED, now)) {
            announcement.setStatus(AnnouncementStatus.EXPIRED);
            announcementRepository.save(announcement);
            eventPublisher.publishEvent(new AnnouncementExpiredEvent(announcement.getId(), now));
        }
    }
}
