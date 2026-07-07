package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {

    Page<TaskActivity> findByTaskIdOrderByOccurredAtDesc(UUID taskId, Pageable pageable);
}
