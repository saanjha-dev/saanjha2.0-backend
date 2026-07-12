package com.saanjha.modules.chat.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A single message. {@code senderId} is null exactly for {@link
 * MessageType#SYSTEM} messages (event-driven, never user-authored - see
 * {@code ChatModuleEventListener}).
 *
 * {@code parentMessageId} models threads: a top-level message has it null; a
 * reply has it set to the thread's root. Only one level deep by design (a
 * reply to a reply is flattened into the same thread, matching the module
 * brief's "never flatten everything into one timeline" instruction the other
 * way - threads are flat *within* a root, not infinitely nested, matching
 * Slack/Discord's own model).
 *
 * {@code searchVector} is maintained by a DB trigger (see V24 migration) -
 * never written from application code.
 */
@Entity
@Table(name = "cht_messages", schema = "cht")
@Getter
@Setter
public class Message extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @Column(name = "parent_message_id", updatable = false)
    private UUID parentMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageType type = MessageType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status = MessageStatus.SENT;

    @Column(columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "reply_count", nullable = false)
    private int replyCount = 0;

    @Column(name = "last_reply_at")
    private Instant lastReplyAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public boolean isThreadRoot() {
        return parentMessageId == null;
    }

    public boolean isDeleted() {
        return status == MessageStatus.DELETED;
    }

    public void recordReply(Instant occurredAt) {
        this.replyCount++;
        this.lastReplyAt = occurredAt;
    }

    public void softDelete(UUID actorId, Instant occurredAt) {
        this.status = MessageStatus.DELETED;
        this.deletedAt = occurredAt;
        this.deletedBy = actorId;
        this.body = null;
    }
}
