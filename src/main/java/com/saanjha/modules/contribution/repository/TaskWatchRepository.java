package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.TaskWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskWatchRepository extends JpaRepository<TaskWatch, UUID> {
}
