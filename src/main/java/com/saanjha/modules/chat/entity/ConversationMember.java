package com.saanjha.modules.chat.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single user's seat in a {@link Conversation}. Exactly one row per
 * (conversation, user) ever exists (DB unique index) - re-joining after
 * LEFT/REMOVED transitions the existing row back to ACTIVE rather than
 * inserting a duplicate, mirroring how tem.tem_membership handles a fresh
 * roster row rather than this handles re-entry in place (the difference is
 * intentional: Team's history is per-stint via a full new row + linked
 * history entries; Chat's is per-row with an implicit history via
 * cht_moderation_actions, since a chat seat carries less lifecycle weight
 * than a team membership).
 *
 * {@code lastReadMessageId}/{@code unreadCount} are the O(1) read cursor;
 * per-message "seen by" detail lives in {@code cht_read_receipts} instead.
 */
@Entity
@Table(name = "cht_conversation_members", schema = "cht")
@Getter
@Setter
public class ConversationMember extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    public boolean isLive() {
        return status == MemberStatus.ACTIVE || status == MemberStatus.MUTED;
    }

    public boolean canSend() {
        return status == MemberStatus.ACTIVE
                && (mutedUntil == null || mutedUntil.isBefore(Instant.now()));
    }

    public void incrementUnread() {
        this.unreadCount++;
    }

    public void markReadThrough(UUID messageId, Instant readAt) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = readAt;
        this.unreadCount = 0;
    }
}
