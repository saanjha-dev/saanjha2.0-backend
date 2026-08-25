package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.TaskLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {

    List<TaskLabel> findByTask_Id(UUID taskId);

    boolean existsByTask_IdAndNameIgnoreCase(UUID taskId, String name);
    Optional<TaskLabel> findByTask_IdAndNameIgnoreCase(UUID taskId, String name);
}
