package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail for every moderation act (mirrors the
 * append-only-ledger convention used by tem_membership_history and
 * ptf_portfolio_entries elsewhere in the codebase). {@code actorId} uses the
 * same {@code SYSTEM_ACTOR_ID} sentinel convention as
 * {@code MembershipHistory.SYSTEM_ACTOR_ID} for event-driven actions (e.g. an
 * auto-lock following ProjectArchivedEvent) that have no human actor.
 */
@Entity
@Table(name = "cht_moderation_actions", schema = "cht")
@Getter
@Setter
public class ModerationAction {

    /** Sentinel actor id for event-driven (non-human-initiated) moderation
     * actions, matching {@code MembershipHistory.SYSTEM_ACTOR_ID}'s convention. */
    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "message_id", updatable = false)
    private UUID messageId;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30, updatable = false)
    private ModerationActionType actionType;

    @Column(length = 1000)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
