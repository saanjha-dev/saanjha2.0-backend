package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.DependencyType;
import com.saanjha.modules.task.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    List<TaskDependency> findByTaskId(UUID taskId);

    List<TaskDependency> findByTaskIdAndType(UUID taskId, DependencyType type);

    boolean existsByTaskIdAndRelatedTaskIdAndType(UUID taskId, UUID relatedTaskId, DependencyType type);
}
