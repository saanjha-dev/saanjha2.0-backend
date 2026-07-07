package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.TaskWatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, UUID> {

    List<TaskWatcher> findByTaskId(UUID taskId);

    Optional<TaskWatcher> findByTaskIdAndUserId(UUID taskId, UUID userId);

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);
}
