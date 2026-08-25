package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    /** Single bulk UPDATE for the "increment unread for every other live member on
     * send" fan-out - avoids the N+1 of loading and re-saving every member row
     * per message, which would not scale on a busy channel. */
    @Modifying
    @Query("""
            UPDATE ConversationMember m SET m.unreadCount = m.unreadCount + 1
            WHERE m.conversationId = :conversationId
              AND m.userId <> :senderId
              AND m.status IN ('ACTIVE','MUTED')
            """)
    int incrementUnreadForOthers(@Param("conversationId") UUID conversationId, @Param("senderId") UUID senderId);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    /** P0-5: batch-loads one viewer's membership rows across several conversations
     * in a single query - avoids an N+1 when listing a project's conversations. */
    List<ConversationMember> findByConversationIdInAndUserId(List<UUID> conversationIds, UUID userId);

    Page<ConversationMember> findByConversationIdAndStatusIn(UUID conversationId, List<MemberStatus> statuses, Pageable pageable);

    List<ConversationMember> findByConversationId(UUID conversationId);

    List<ConversationMember> findByConversationIdAndStatusIn(UUID conversationId, List<MemberStatus> statuses);

    Page<ConversationMember> findByUserIdAndStatusIn(UUID userId, List<MemberStatus> statuses, Pageable pageable);

    boolean existsByConversationIdAndUserIdAndStatusIn(UUID conversationId, UUID userId, List<MemberStatus> statuses);

    long countByConversationIdAndStatusIn(UUID conversationId, List<MemberStatus> statuses);
}
