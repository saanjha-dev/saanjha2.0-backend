package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.AdminNote;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminNoteRepository extends JpaRepository<AdminNote, UUID> {

    List<AdminNote> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTargetType targetType, UUID targetId);
}
