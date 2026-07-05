package com.saanjha.modules.application.repository;

import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.entity.ProjectApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication, UUID> {

    Page<ProjectApplication> findByApplicantId(UUID applicantId, Pageable pageable);

    Page<ProjectApplication> findByProjectId(UUID projectId, Pageable pageable);

    Page<ProjectApplication> findByProjectIdAndStatus(UUID projectId, ApplicationStatus status, Pageable pageable);

    boolean existsByProjectIdAndApplicantIdAndStatusIn(UUID projectId, UUID applicantId, List<ApplicationStatus> statuses);

    /**
     * Most recent terminal application by this applicant to this project —
     * used to enforce the reapplication cooldown window.
     */
    Optional<ProjectApplication> findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(UUID projectId, UUID applicantId);

    long countByApplicantIdAndStatusIn(UUID applicantId, List<ApplicationStatus> statuses);

    /** Backs the expiration sweep: open applications past their deadline. */
    List<ProjectApplication> findByStatusInAndExpiresAtBefore(List<ApplicationStatus> openStatuses, Instant cutoff);

    /**
     * Pessimistic write lock, closing the race window between reading the
     * current status and writing a new one under concurrent review actions
     * or an applicant withdrawing mid-review (Spec: "Applicant withdraws
     * while owner accepts").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectApplication> findWithLockById(UUID id);

    @Query("SELECT a.status as status, COUNT(a) as count FROM ProjectApplication a WHERE a.projectId = :projectId GROUP BY a.status")
    List<StatusCount> countByStatusForProject(@Param("projectId") UUID projectId);

    interface StatusCount {
        ApplicationStatus getStatus();
        long getCount();
    }
}
