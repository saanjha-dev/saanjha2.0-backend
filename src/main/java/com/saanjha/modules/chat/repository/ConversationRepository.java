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

    /** Idempotent lookup for role-channel sync: finds a specific named conversation
     *  (typically GROUP) within a project. Used by {@code getOrCreateProjectGroupConversation}. */
    Optional<Conversation> findFirstByProjectIdAndTypeAndName(UUID projectId, ConversationType type, String name);

    /** Backs {@code getOrCreateDirectConversation}'s find step - paired with
     * {@code uq_conv_direct_pair} (V28) for the race-safe create step. Callers
     * must pass the pair already canonicalized (low &lt; high). */
    Optional<Conversation> findByTypeAndDirectUserLowAndDirectUserHigh(
            ConversationType type, UUID directUserLow, UUID directUserHigh);

    List<Conversation> findByProjectId(UUID projectId);

    /** P0-5 (Project Conversation Query): paginated variant of the above for the
     * "give me this project's conversations" REST endpoint - avoids the frontend
     * loading every conversation just to filter by project client-side. */
    Page<Conversation> findByProjectId(UUID projectId, Pageable pageable);

    List<Conversation> findByTeamId(UUID teamId);

    /** Pessimistic write lock - serialization point for concurrent member-count /
     * message-count mutations, same pattern as TeamRepository's findWithLockByProjectId. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversation> findWithLockById(UUID id);

    Page<Conversation> findByIdIn(List<UUID> ids, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Conversation c
            JOIN ConversationMember m ON c.id = m.conversationId
            WHERE m.userId = :userId
              AND m.status IN :statuses
              AND c.projectId IS NULL
            """)
    Page<Conversation> findGlobalConversationsForUser(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("statuses") List<com.saanjha.modules.chat.entity.MemberStatus> statuses,
            Pageable pageable);
}
