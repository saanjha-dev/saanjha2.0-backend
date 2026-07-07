package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.TaskHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistory, UUID> {

    Page<TaskHistory> findByTaskIdOrderByChangedAtDesc(UUID taskId, Pageable pageable);
}
