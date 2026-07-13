package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.entity.Report;
import com.saanjha.modules.admin.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatusInOrderByCreatedAtAsc(List<ReportStatus> statuses, Pageable pageable);

    Page<Report> findByAssignedModeratorIdAndStatusInOrderByCreatedAtAsc(UUID moderatorId, List<ReportStatus> statuses, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTargetType targetType, UUID targetId, Pageable pageable);

    Page<Report> findByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId, Pageable pageable);

    long countByTargetTypeAndTargetId(ModerationTargetType targetType, UUID targetId);

    long countByStatus(ReportStatus status);
}
