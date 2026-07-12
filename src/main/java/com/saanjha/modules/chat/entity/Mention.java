package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One row per @mention successfully parsed and validated (the mentioned
 * user must be a live conversation member - see MentionService) in a
 * message. Feeds MentionCreatedEvent, which Notification (not Chat) turns
 * into a user-visible alert. */
@Entity
@Table(name = "cht_mentions", schema = "cht")
@Getter
@Setter
public class Mention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "mentioned_user_id", nullable = false, updatable = false)
    private UUID mentionedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
