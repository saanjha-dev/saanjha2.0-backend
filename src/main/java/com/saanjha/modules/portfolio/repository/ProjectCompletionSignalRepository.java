package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.ProjectCompletionSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectCompletionSignalRepository extends JpaRepository<ProjectCompletionSignal, UUID> {

    Optional<ProjectCompletionSignal> findByProjectId(UUID projectId);
}
