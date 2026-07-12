package com.saanjha.modules.chat.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Chat module. Chat never calls Notification
 * directly (module brief's "NOTIFICATION INTEGRATION" rule) - every event
 * here is Notification's future input, exactly like every other module's
 * event contract in this codebase. All payloads are flat value records (no
 * entity references), consistent with Team/Task/Project/Contribution's own
 * event design constraint.
 */
public final class ChatEvents {

    private ChatEvents() {
    }

    /** Fired once, the moment a conversation is created (user-initiated or auto-provisioned). */
    public record ConversationCreatedEvent(
            UUID conversationId, String type, UUID projectId, UUID teamId, UUID createdBy, Instant occurredAt
    ) {}

    /** Consumers: Notification (unread alert to offline members), Discovery (future - activity signal). */
    public record MessageSentEvent(
            UUID messageId, UUID conversationId, UUID senderId, String type,
            UUID parentMessageId, boolean hasAttachments, Instant occurredAt
    ) {}

    public record MessageEditedEvent(UUID messageId, UUID conversationId, UUID editorId, Instant occurredAt) {}

    public record MessageDeletedEvent(UUID messageId, UUID conversationId, UUID deletedBy, Instant occurredAt) {}

    public record ReactionAddedEvent(UUID messageId, UUID conversationId, UUID userId, String emoji, Instant occurredAt) {}

    public record ReactionRemovedEvent(UUID messageId, UUID conversationId, UUID userId, String emoji, Instant occurredAt) {}

    /** Consumers: Notification (mention alert). Fired once per distinct mentioned user per message. */
    public record MentionCreatedEvent(UUID messageId, UUID conversationId, UUID mentionedUserId, UUID mentionedBy, Instant occurredAt) {}

    public record ConversationArchivedEvent(UUID conversationId, String reason, Instant occurredAt) {}

    public record ConversationLockedEvent(UUID conversationId, String reason, Instant occurredAt) {}

    public record PinnedMessageCreatedEvent(UUID conversationId, UUID messageId, UUID pinnedBy, Instant occurredAt) {}

    /** Consumers: Notification (future - "seen by everyone" digest suppression). Fired at most once per
     * (conversation, user) per read-cursor advance, not per message, to avoid a fan-out storm in busy channels. */
    public record ReadReceiptUpdatedEvent(UUID conversationId, UUID userId, UUID lastReadMessageId, Instant occurredAt) {}
}
