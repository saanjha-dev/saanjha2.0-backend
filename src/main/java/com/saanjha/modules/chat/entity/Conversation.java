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
 * Chat's aggregate root. One row per conversation of any {@link ConversationType}.
 *
 * {@code projectId}/{@code teamId} are logical links only (Boundary Rule: no
 * FK across schemas) and are both null for user-initiated DIRECT_MESSAGE/
 * GROUP/SUPPORT conversations. For PROJECT_TEAM and PROJECT_ANNOUNCEMENTS,
 * both are set at auto-provisioning time and never change afterward - a
 * conversation never migrates between projects.
 *
 * {@code memberCount}/{@code messageCount} are denormalized counters,
 * maintained incrementally at write time (same convention as
 * tem.tem_teams's roster counters) rather than recomputed from child tables
 * on every read.
 */
@Entity
@Table(name = "cht_conversations", schema = "cht")
@Getter
@Setter
public class Conversation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(length = 150)
    private String name;

    @Column(length = 500)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";

    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    @Column(name = "message_count", nullable = false)
    private long messageCount = 0;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean acceptsNewMessages() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean acceptsNewMembers() {
        return status == ConversationStatus.ACTIVE;
    }

    public void recordIncomingMessage(String preview, Instant occurredAt) {
        this.messageCount++;
        this.lastMessageAt = occurredAt;
        this.lastMessagePreview = preview;
    }
}
