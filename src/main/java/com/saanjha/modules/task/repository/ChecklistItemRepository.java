package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    List<ChecklistItem> findByTask_IdOrderByPositionAsc(UUID taskId);

    long countByTask_Id(UUID taskId);

    long countByTask_IdAndCompletedTrue(UUID taskId);
}
