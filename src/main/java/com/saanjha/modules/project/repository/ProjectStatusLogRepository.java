package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.ProjectStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectStatusLogRepository extends JpaRepository<ProjectStatusLog, UUID> {

    List<ProjectStatusLog> findByProjectIdOrderByChangedAtAsc(UUID projectId);
}
