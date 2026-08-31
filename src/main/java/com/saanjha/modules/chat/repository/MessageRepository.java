package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

        /**
         * Cursor pagination for history: strictly-before-cursor, newest first.
         * Avoids OFFSET pagination's cost on a hot, ever-growing table.
         */
        @Query("""
                        SELECT m FROM Message m
                        WHERE m.conversationId = :conversationId
                          AND m.parentMessageId IS NULL
                          AND m.deletedAt IS NULL
                          AND (CAST(:clearedAt AS timestamp) IS NULL OR m.createdAt > :clearedAt)
                        ORDER BY m.createdAt DESC
                        """)
        List<Message> findConversationHistoryInitial(
                        @Param("conversationId") UUID conversationId,
                        @Param("clearedAt") Instant clearedAt,
                        Pageable pageable);

        @Query("""
                        SELECT m FROM Message m
                        WHERE m.conversationId = :conversationId
                          AND m.parentMessageId IS NULL
                          AND m.deletedAt IS NULL
                          AND m.createdAt < :cursor
                          AND (CAST(:clearedAt AS timestamp) IS NULL OR m.createdAt > :clearedAt)
                        ORDER BY m.createdAt DESC
                        """)
        List<Message> findConversationHistoryBeforeCursor(
                        @Param("conversationId") UUID conversationId,
                        @Param("cursor") Instant cursor,
                        @Param("clearedAt") Instant clearedAt,
                        Pageable pageable);

        @Query("""
                        SELECT m FROM Message m
                        WHERE m.parentMessageId = :rootMessageId
                          AND m.deletedAt IS NULL
                          AND (CAST(:clearedAt AS timestamp) IS NULL OR m.createdAt > :clearedAt)
                        ORDER BY m.createdAt DESC
                        """)
        List<Message> findThreadRepliesInitial(
                        @Param("rootMessageId") UUID rootMessageId,
                        @Param("clearedAt") Instant clearedAt,
                        Pageable pageable);

        @Query("""
                        SELECT m FROM Message m
                        WHERE m.parentMessageId = :rootMessageId
                          AND m.deletedAt IS NULL
                          AND m.createdAt < :cursor
                          AND (CAST(:clearedAt AS timestamp) IS NULL OR m.createdAt > :clearedAt)
                        ORDER BY m.createdAt DESC
                        """)
        List<Message> findThreadRepliesBeforeCursor(
                        @Param("rootMessageId") UUID rootMessageId,
                        @Param("cursor") Instant cursor,
                        @Param("clearedAt") Instant clearedAt,
                        Pageable pageable);

        Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

        @Query(value = """
                        SELECT * FROM cht.cht_messages
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                          AND search_vector @@ plainto_tsquery('english', :query)
                        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC, created_at DESC
                        """, countQuery = """
                        SELECT count(*) FROM cht.cht_messages
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                          AND search_vector @@ plainto_tsquery('english', :query)
                        """, nativeQuery = true)
        Page<Message> searchWithinConversation(@Param("conversationId") UUID conversationId,
                        @Param("query") String query, Pageable pageable);

        @Query(value = """
                        SELECT m.* FROM cht.cht_messages m
                        JOIN cht.cht_conversation_members cm ON cm.conversation_id = m.conversation_id
                        WHERE cm.user_id = :userId AND cm.status IN ('ACTIVE','MUTED')
                          AND m.deleted_at IS NULL
                          AND m.search_vector @@ plainto_tsquery('english', :query)
                          AND (:senderId IS NULL OR m.sender_id = :senderId)
                          AND (:fromDate IS NULL OR m.created_at >= :fromDate)
                          AND (:toDate IS NULL OR m.created_at <= :toDate)
                        ORDER BY ts_rank(m.search_vector, plainto_tsquery('english', :query)) DESC, m.created_at DESC
                        """, countQuery = """
                        SELECT count(*) FROM cht.cht_messages m
                        JOIN cht.cht_conversation_members cm ON cm.conversation_id = m.conversation_id
                        WHERE cm.user_id = :userId AND cm.status IN ('ACTIVE','MUTED')
                          AND m.deleted_at IS NULL
                          AND m.search_vector @@ plainto_tsquery('english', :query)
                          AND (:senderId IS NULL OR m.sender_id = :senderId)
                          AND (:fromDate IS NULL OR m.created_at >= :fromDate)
                          AND (:toDate IS NULL OR m.created_at <= :toDate)
                        """, nativeQuery = true)
        Page<Message> searchAcrossMyConversations(
                        @Param("userId") UUID userId,
                        @Param("query") String query,
                        @Param("senderId") UUID senderId,
                        @Param("fromDate") Instant fromDate,
                        @Param("toDate") Instant toDate,
                        Pageable pageable);

        long countByConversationIdAndParentMessageIdIsNull(UUID conversationId);
}
