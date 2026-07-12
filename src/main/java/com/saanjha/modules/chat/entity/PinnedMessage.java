package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** {@code unpinnedAt == null} means currently pinned (see the migration's
 * partial unique index enforcing at most one active pin per message). A
 * message may be pinned, unpinned, and re-pinned over time - each pin is its
 * own row, so history of "who pinned/unpinned when" is preserved rather than
 * overwritten. */
@Entity
@Table(name = "cht_pinned_messages", schema = "cht")
@Getter
@Setter
public class PinnedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "pinned_by", nullable = false, updatable = false)
    private UUID pinnedBy;

    @Column(name = "pinned_at", nullable = false, updatable = false)
    private Instant pinnedAt = Instant.now();

    @Column(name = "unpinned_at")
    private Instant unpinnedAt;

    @Column(name = "unpinned_by")
    private UUID unpinnedBy;

    public boolean isActive() {
        return unpinnedAt == null;
    }
}
