package com.saanjha.modules.chat.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public class ChatRequestDTOs {

    public record CreateConversationRequest(
            @NotBlank @Pattern(regexp = "^(DIRECT_MESSAGE|GROUP|SUPPORT)$",
                    message = "Only DIRECT_MESSAGE, GROUP, and SUPPORT may be created directly - PROJECT_TEAM/PROJECT_ANNOUNCEMENTS/SYSTEM are event-provisioned")
            String type,

            @Size(max = 150) String name,
            @Size(max = 500) String topic,

            /** Optional project linkage for GROUP conversations. When set, the
             *  conversation's {@code project_id} column is populated, making it
             *  discoverable via {@code GET /v1/projects/{projectId}/conversations}. */
            UUID projectId,

            @NotEmpty(message = "At least one initial member is required")
            List<UUID> memberUserIds
    ) {}

    public record UpdateConversationSettingsRequest(
            Boolean onlyAdminsCanPost,
            Boolean allowThreads,
            Boolean allowReactions,
            @Min(0) @Max(3600) Integer slowModeSeconds,
            Boolean allowExternalReferences
    ) {}

    public record SendMessageRequest(
            @NotBlank(message = "Message type is required")
            String type,

            @Size(max = 8000, message = "Message body cannot exceed 8000 characters")
            String body,

            UUID parentMessageId,

            /** CODE-type language tag, reference ids for TASK_REFERENCE/PROJECT_REFERENCE/
             * PORTFOLIO_REFERENCE, etc. - free-form, validated per-type in MessageService. */
            java.util.Map<String, Object> metadata,

            List<AttachmentRequest> attachments
    ) {}

    public record AttachmentRequest(
            @NotBlank String filename,
            @NotBlank String mimeType,
            @NotBlank String checksum,
            @Positive long sizeBytes,
            @NotBlank String storageProvider,
            @NotBlank String storageReference
    ) {}

    public record EditMessageRequest(
            @NotBlank @Size(max = 8000) String body
    ) {}

    /**
     * FIX (P0-4, Chat Reaction Persistence): {@code messageId} was missing
     * entirely — the WebSocket handler had no way to know which message a
     * reaction targeted, so it could only re-broadcast the raw payload
     * rather than ever calling {@code ReactionService}.
     */
    public record ReactRequest(
            @NotNull UUID messageId,
            @NotBlank @Size(max = 64) String emoji
    ) {}

    public record AddMemberRequest(
            @NotNull UUID userId
    ) {}

    public record RemoveMemberRequest(
            @Size(max = 500) String reason
    ) {}

    public record MuteMemberRequest(
            @NotNull @Min(1) Integer durationMinutes,
            @Size(max = 500) String reason
    ) {}

    public record BlockUserRequest(
            @NotNull UUID userId,
            @Size(max = 500) String reason
    ) {}

    public record LockConversationRequest(
            @Size(max = 500) String reason
    ) {}

    public record SaveDraftRequest(
            @NotBlank @Size(max = 8000) String body,
            UUID parentMessageId
    ) {}

    public record MarkReadRequest(
            @NotNull UUID lastReadMessageId
    ) {}

    public record SearchRequest(
            @NotBlank @Size(min = 2, max = 200) String query,
            UUID senderId,
            java.time.Instant fromDate,
            java.time.Instant toDate
    ) {}
}
