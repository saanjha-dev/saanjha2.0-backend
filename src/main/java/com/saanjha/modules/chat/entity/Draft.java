package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** At most one top-level draft and one per-thread draft, per (conversation,
 * user) - see the migration's two partial unique indexes. Drafts are plain
 * REST-managed state (save-on-blur/periodic autosave from the client), not
 * WebSocket - unlike Typing, a draft is meant to survive a page reload. */
@Entity
@Table(name = "cht_drafts", schema = "cht")
@Getter
@Setter
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "parent_message_id", updatable = false)
    private UUID parentMessageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
