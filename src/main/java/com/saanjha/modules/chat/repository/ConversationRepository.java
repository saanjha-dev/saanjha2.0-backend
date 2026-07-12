package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.Conversation;
import com.saanjha.modules.chat.entity.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByProjectIdAndType(UUID projectId, ConversationType type);

    List<Conversation> findByProjectId(UUID projectId);

    List<Conversation> findByTeamId(UUID teamId);

    /** Pessimistic write lock - serialization point for concurrent member-count /
     * message-count mutations, same pattern as TeamRepository's findWithLockByProjectId. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversation> findWithLockById(UUID id);

    Page<Conversation> findByIdIn(List<UUID> ids, Pageable pageable);
}
