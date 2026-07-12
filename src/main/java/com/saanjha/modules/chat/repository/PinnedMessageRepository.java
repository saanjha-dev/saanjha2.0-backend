package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.PinnedMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, UUID> {

    Page<PinnedMessage> findByConversationIdAndUnpinnedAtIsNullOrderByPinnedAtDesc(UUID conversationId, Pageable pageable);

    Optional<PinnedMessage> findByMessageIdAndUnpinnedAtIsNull(UUID messageId);

    long countByConversationIdAndUnpinnedAtIsNull(UUID conversationId);
}
