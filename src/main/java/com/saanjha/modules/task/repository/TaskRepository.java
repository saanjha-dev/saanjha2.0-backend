package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.Task;
import com.saanjha.modules.task.entity.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Page<Task> findByProjectId(UUID projectId, Pageable pageable);

    /** Non-paginated variant for exhaustive system-triggered operations (e.g. archiving every task when a project ends) — never used for a user-facing list endpoint. */
    List<Task> findByProjectId(UUID projectId);

    Page<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status, Pageable pageable);

    Page<Task> findByAssigneeId(UUID assigneeId, Pageable pageable);

    Page<Task> findByAssigneeIdAndStatus(UUID assigneeId, TaskStatus status, Pageable pageable);

    List<Task> findByProjectIdAndAssigneeIdAndStatusIn(UUID projectId, UUID assigneeId, List<TaskStatus> statuses);

    /** Backs the hard-cap-at-3-IN_PROGRESS rule (MES H.2 #7). */
    long countByAssigneeIdAndStatus(UUID assigneeId, TaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Task> findWithLockById(UUID id);

    @Query("SELECT t.status as status, COUNT(t) as count FROM Task t WHERE t.projectId = :projectId GROUP BY t.status")
    List<StatusCount> countByStatusForProject(@Param("projectId") UUID projectId);

    // Bypassing HQL to use PostgreSQL's native EXTRACT(EPOCH)
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) FROM task " +
            "WHERE project_id = :projectId AND status = 'DONE' AND started_at IS NOT NULL AND completed_at IS NOT NULL",
            nativeQuery = true)
    Double averageCycleTimeSeconds(@Param("projectId") UUID projectId);

    // Bypassing HQL to use PostgreSQL's native EXTRACT(EPOCH)
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - created_at))) FROM task " +
            "WHERE project_id = :projectId AND status = 'DONE' AND completed_at IS NOT NULL",
            nativeQuery = true)
    Double averageLeadTimeSeconds(@Param("projectId") UUID projectId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TaskStatus status);

    interface StatusCount {
        TaskStatus getStatus();
        long getCount();
    }
}
