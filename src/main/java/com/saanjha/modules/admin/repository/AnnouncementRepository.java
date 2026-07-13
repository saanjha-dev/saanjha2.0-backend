package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.Announcement;
import com.saanjha.modules.admin.entity.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Announcement> findByStatusAndStartsAtBefore(AnnouncementStatus status, Instant now);

    List<Announcement> findByStatusAndExpiresAtBefore(AnnouncementStatus status, Instant now);

    /** Currently live announcements a client should render — status PUBLISHED and not yet expired. */
    List<Announcement> findByStatusAndExpiresAtAfterOrExpiresAtIsNull(AnnouncementStatus status, Instant now);
}
