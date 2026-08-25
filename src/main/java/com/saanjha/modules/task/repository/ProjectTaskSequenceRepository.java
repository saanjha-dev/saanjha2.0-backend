package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.ProjectTaskSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectTaskSequenceRepository extends JpaRepository<ProjectTaskSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProjectTaskSequence s WHERE s.projectId = :projectId")
    Optional<ProjectTaskSequence> findByProjectIdForUpdate(@Param("projectId") UUID projectId);
}
