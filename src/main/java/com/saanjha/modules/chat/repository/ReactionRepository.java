package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    List<Reaction> findByMessageIdIn(List<UUID> messageIds);

    List<Reaction> findByMessageId(UUID messageId);

    Optional<Reaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    void deleteByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    @Query("SELECT r.emoji as emoji, COUNT(r) as cnt FROM Reaction r WHERE r.messageId = :messageId GROUP BY r.emoji")
    List<EmojiCount> countByEmojiForMessage(@Param("messageId") UUID messageId);

    interface EmojiCount {
        String getEmoji();
        long getCnt();
    }
}
