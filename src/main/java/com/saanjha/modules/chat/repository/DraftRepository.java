package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DraftRepository extends JpaRepository<Draft, UUID> {

    Optional<Draft> findByConversationIdAndUserIdAndParentMessageIdIsNull(UUID conversationId, UUID userId);

    Optional<Draft> findByConversationIdAndUserIdAndParentMessageId(UUID conversationId, UUID userId, UUID parentMessageId);

    List<Draft> findByUserId(UUID userId);

    void deleteByConversationIdAndUserIdAndParentMessageIdIsNull(UUID conversationId, UUID userId);

    void deleteByConversationIdAndUserIdAndParentMessageId(UUID conversationId, UUID userId, UUID parentMessageId);
}
