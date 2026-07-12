package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.ModerationAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    Page<ModerationAction> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
}
