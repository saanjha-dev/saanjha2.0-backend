package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-message, per-user "read" audit trail - powers a "seen by" list on a
 * message. The O(1) unread-count/last-seen cursor lives on {@link
 * ConversationMember} instead; this table is the detailed record behind it,
 * written alongside the cursor update in the same transaction (see
 * ReadReceiptService), never instead of it.
 */
@Entity
@Table(name = "cht_read_receipts", schema = "cht")
@Getter
@Setter
public class ReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "read_at", nullable = false, updatable = false)
    private Instant readAt = Instant.now();
}
