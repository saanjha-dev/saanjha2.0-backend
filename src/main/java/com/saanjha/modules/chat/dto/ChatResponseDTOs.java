package com.saanjha.modules.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatResponseDTOs {

    public record ConversationResponse(
            UUID id, UUID projectId, UUID teamId, String type, String status,
            String name, String topic, ConversationSettingsResponse settings,
            int memberCount, long messageCount, Instant lastMessageAt, String lastMessagePreview,
            Instant createdAt
    ) {}

    public record ConversationSettingsResponse(
            boolean onlyAdminsCanPost, boolean allowThreads, boolean allowReactions,
            int slowModeSeconds, boolean allowExternalReferences
    ) {}

    public record ConversationSummaryResponse(
            UUID id, String type, String name, int memberCount,
            Instant lastMessageAt, String lastMessagePreview, int unreadCount,
            UUID directMessagePeerId
    ) {}

    public record ConversationMemberResponse(
            UUID id, UUID userId, String role, String status,
            int unreadCount, Instant lastReadAt, Instant mutedUntil, Instant joinedAt
    ) {}

    public record MessageResponse(
            UUID id, UUID conversationId, UUID senderId, UUID parentMessageId,
            String type, String status, String body, Map<String, Object> metadata,
            int replyCount, Instant lastReplyAt,
            List<ReactionSummaryResponse> reactions,
            List<AttachmentResponse> attachments,
            Instant createdAt, Instant editedAt, Instant deletedAt
    ) {}

    public record ReactionSummaryResponse(String emoji, long count, boolean reactedByMe) {}

    /**
     * FIX (P0-4, Chat Reaction Persistence): what {@code
     * ChatWebSocketController} broadcasts to {@code /topic/conversations/
     * {id}/reactions} after a reaction is actually persisted via {@code
     * ReactionService} — the full current per-emoji summary for the
     * message, not just the raw client payload, so every subscriber's view
     * converges on the same DB-backed state the message-history endpoint
     * would return on reload. {@code reactedByMe} in the nested summaries
     * is computed without a specific viewer (always {@code false}) since a
     * single broadcast fans out to every subscriber, not one viewer.
     */
    public record ReactionEventResponse(
            UUID conversationId, UUID messageId, UUID actorUserId, String emoji, String action,
            List<ReactionSummaryResponse> reactions, Instant occurredAt
    ) {}

    public record AttachmentResponse(
            UUID id, String filename, String mimeType, long sizeBytes,
            String storageProvider, String storageReference
    ) {}

    public record PinnedMessageResponse(
            UUID id, UUID messageId, UUID pinnedBy, Instant pinnedAt, MessageResponse message
    ) {}

    public record DraftResponse(UUID conversationId, UUID parentMessageId, String body, Instant updatedAt) {}

    public record ModerationActionResponse(
            UUID id, String actionType, UUID targetUserId, UUID messageId, String reason, UUID actorId, Instant createdAt
    ) {}

    public record UnreadSummaryResponse(UUID conversationId, int unreadCount, Instant lastReadAt) {}

    public record ChatMutationResponse(String message, String status) {}

    /** WebSocket-broadcast payloads (STOMP destinations - see ChatWebSocketController). */
    public record TypingEvent(UUID conversationId, UUID userId, boolean typing) {}

    public record PresenceEvent(UUID userId, String status, Instant since) {}
}
