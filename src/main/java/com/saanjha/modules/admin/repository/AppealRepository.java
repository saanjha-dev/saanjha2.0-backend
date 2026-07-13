package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.Appeal;
import com.saanjha.modules.admin.entity.AppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, UUID> {

    Page<Appeal> findByStatusInOrderByCreatedAtAsc(List<AppealStatus> statuses, Pageable pageable);

    Page<Appeal> findByAppellantUserIdOrderByCreatedAtDesc(UUID appellantUserId, Pageable pageable);

    Optional<Appeal> findByModerationActionIdAndStatusIn(UUID moderationActionId, List<AppealStatus> statuses);

    long countByStatus(AppealStatus status);
}
