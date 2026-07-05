package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    Page<Project> findByLeadUserId(UUID leadUserId, Pageable pageable);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    /**
     * Backs the "Ghosting Leads" scheduled sweep (Spec H.2 #6): projects stuck
     * in RECRUITING for longer than the configured grace period.
     */
    List<Project> findByStatusAndRecruitingStartedAtBefore(ProjectStatus status, Instant cutoff);

    /**
     * Pessimistic write lock used during status transitions to close the race
     * window between "read current status" and "write new status" under
     * concurrent requests (e.g. two rapid-fire PATCH /status calls).
     * Combined with @Version, this belt-and-suspenders approach fails fast
     * and deterministically rather than silently applying a stale transition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Project> findWithLockById(UUID id);
}
