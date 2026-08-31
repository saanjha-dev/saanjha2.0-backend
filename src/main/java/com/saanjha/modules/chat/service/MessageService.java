package com.saanjha.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.AttachmentRequest;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.SendMessageRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.AttachmentResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.MessageResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ReactionSummaryResponse;
import com.saanjha.modules.chat.entity.*;
import com.saanjha.modules.chat.event.ChatEvents.MessageDeletedEvent;
import com.saanjha.modules.chat.event.ChatEvents.MessageEditedEvent;
import com.saanjha.modules.chat.event.ChatEvents.MessageSentEvent;
import com.saanjha.modules.chat.repository.*;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Owns Message writes/reads, thread rollups, and attachment-metadata
 * registration. Reactions live in {@link ReactionService}; unread-count
 * fan-out and read-cursor advance live in {@link ReadReceiptService} - this
 * class orchestrates them for a send but doesn't duplicate their logic.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int PREVIEW_MAX_LENGTH = 120;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final AttachmentRepository attachmentRepository;
    private final ReactionRepository reactionRepository;
    private final ConversationService conversationService;
    private final ReadReceiptService readReceiptService;
    private final MentionService mentionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Conversation conversation = conversationRepository.findWithLockById(conversationId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
            if (!conversation.acceptsNewMessages()) {
                throw new AppException(ErrorCode.CHAT_CONVERSATION_READ_ONLY);
            }
            MessageType type = parseType(request.type());

            Message parent = null;
            if (request.parentMessageId() != null) {
                parent = messageRepository.findByIdAndConversationId(request.parentMessageId(), conversationId)
                        .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Parent message not found in this conversation."));
                if (!parent.isThreadRoot()) {
                    throw new AppException(ErrorCode.CHAT_THREAD_DEPTH_EXCEEDED);
                }
            }

            Message message = new Message();
            message.setConversationId(conversationId);
            message.setSenderId(senderId);
            message.setParentMessageId(parent != null ? parent.getId() : null);
            message.setType(type);
            message.setStatus(MessageStatus.SENT);
            message.setBody(request.body());
            message.setMetadataJson(writeMetadata(request.metadata()));
            message = messageRepository.save(message);

            List<Attachment> attachments = List.of();
            if (request.attachments() != null && !request.attachments().isEmpty()) {
                attachments = registerAttachments(message.getId(), request.attachments());
            }

            Instant now = Instant.now();
            if (parent != null) {
                parent.recordReply(now);
                messageRepository.save(parent);
            }

            conversation.recordIncomingMessage(buildPreview(message), now);
            conversationRepository.save(conversation);

            readReceiptService.incrementUnreadForOthers(conversationId, senderId);
            mentionService.processMentions(message.getId(), conversationId, senderId, message.getBody());

            meterRegistry.counter("chat.message.sent", "type", type.name()).increment();
            eventPublisher.publishEvent(new MessageSentEvent(
                    message.getId(), conversationId, senderId, type.name(),
                    message.getParentMessageId(), !attachments.isEmpty(), now));

            return mapToResponse(message, attachments, List.of(), senderId);
        } finally {
            sample.stop(meterRegistry.timer("chat.message.send.latency"));
        }
    }

    /**
     * Inserts an event-driven SYSTEM message (module brief: "These are
     * event-driven. Never manually inserted.") - called only from {@code
     * ChatModuleEventListener}, never from a REST controller. senderId is
     * null; body is a short, human-readable line, metadata carries the
     * structured event detail (event type, actor) for a client that wants
     * to render something richer than plain text.
     */
    @Transactional
    public void postSystemMessage(UUID conversationId, String body, Map<String, Object> metadata) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setSenderId(null);
            message.setType(MessageType.SYSTEM);
            message.setStatus(MessageStatus.SENT);
            message.setBody(body);
            message.setMetadataJson(writeMetadata(metadata));
            messageRepository.save(message);

            Instant now = Instant.now();
            conversation.recordIncomingMessage(buildPreview(message), now);
            conversationRepository.save(conversation);
            // Sentinel "sender" (all-zero UUID, same convention as
            // ModerationAction.SYSTEM_ACTOR_ID) rather than null: the bulk
            // UPDATE's "userId <> :senderId" clause would match zero rows
            // under SQL NULL semantics if senderId itself were null.
            readReceiptService.incrementUnreadForOthers(conversationId, new UUID(0L, 0L));
            meterRegistry.counter("chat.message.sent", "type", "SYSTEM").increment();
        });
    }

    @Transactional
    public MessageResponse editMessage(UUID conversationId, UUID messageId, UUID editorId, String newBody) {
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation."));
        if (message.isDeleted()) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_ALREADY_DELETED);
        }
        message.setBody(newBody);
        message.setStatus(MessageStatus.EDITED);
        message.setEditedAt(Instant.now());
        messageRepository.save(message);

        mentionService.processMentions(message.getId(), conversationId, editorId, newBody);
        eventPublisher.publishEvent(new MessageEditedEvent(messageId, conversationId, editorId, Instant.now()));

        List<Attachment> attachments = attachmentRepository.findByMessageId(messageId);
        List<ReactionSummaryResponse> reactions = summarize(messageId, editorId);
        return mapToResponse(message, attachments, reactions, editorId);
    }

    @Transactional
    public void deleteMessage(UUID conversationId, UUID messageId, UUID actorId, boolean isModeratorAction) {
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation."));
        if (message.isDeleted()) {
            return; // idempotent
        }
        message.softDelete(actorId, Instant.now());
        messageRepository.save(message);

        if (isModeratorAction) {
            conversationService.recordModeration(conversationId, messageId, message.getSenderId(),
                    ModerationActionType.DELETE_MESSAGE, "Moderator deletion", actorId);
        }
        meterRegistry.counter("chat.message.deleted").increment();
        eventPublisher.publishEvent(new MessageDeletedEvent(messageId, conversationId, actorId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(UUID conversationId, Instant cursor, Integer limit, UUID viewerId) {
        int size = (limit == null || limit <= 0 || limit > 200) ? DEFAULT_PAGE_SIZE : limit;
        var pageable = PageRequest.of(0, size);
        Instant clearedAt = conversationService.getClearedAt(conversationId, viewerId);
        List<Message> messages;
        if (cursor == null) {
            messages = messageRepository.findConversationHistoryInitial(conversationId, clearedAt, pageable);
        } else {
            messages = messageRepository.findConversationHistoryBeforeCursor(conversationId, cursor, clearedAt, pageable);
        }
        return batchMapToResponses(messages, viewerId);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getThreadReplies(UUID conversationId, UUID rootMessageId, Instant cursor, Integer limit, UUID viewerId) {
        messageRepository.findByIdAndConversationId(rootMessageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Thread root message not found in this conversation."));
        int size = (limit == null || limit <= 0 || limit > 200) ? DEFAULT_PAGE_SIZE : limit;
        var pageable = PageRequest.of(0, size);
        Instant clearedAt = conversationService.getClearedAt(conversationId, viewerId);
        List<Message> messages;
        if (cursor == null) {
            messages = messageRepository.findThreadRepliesInitial(rootMessageId, clearedAt, pageable);
        } else {
            messages = messageRepository.findThreadRepliesBeforeCursor(rootMessageId, cursor, clearedAt, pageable);
        }
        return batchMapToResponses(messages, viewerId);
    }

    // -------------------------------------------------------------------
    // Batch loading (no N+1): reactions and attachments for a page of
    // messages are fetched in two queries total, not one per message.
    // -------------------------------------------------------------------

    public List<MessageResponse> batchMapToResponses(List<Message> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = messages.stream().map(Message::getId).toList();

        Map<UUID, List<Reaction>> reactionsByMessage = reactionRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(Reaction::getMessageId));
        Map<UUID, List<Attachment>> attachmentsByMessage = attachmentRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(Attachment::getMessageId));

        return messages.stream()
                .map(m -> {
                    List<Reaction> reactions = reactionsByMessage.getOrDefault(m.getId(), List.of());
                    List<ReactionSummaryResponse> summaries = summarizeReactions(reactions, viewerId);
                    List<Attachment> attachments = attachmentsByMessage.getOrDefault(m.getId(), List.of());
                    return mapToResponse(m, attachments, summaries, viewerId);
                })
                .toList();
    }

    private List<ReactionSummaryResponse> summarize(UUID messageId, UUID viewerId) {
        return summarizeReactions(reactionRepository.findByMessageId(messageId), viewerId);
    }

    private List<ReactionSummaryResponse> summarizeReactions(List<Reaction> reactions, UUID viewerId) {
        Map<String, List<Reaction>> byEmoji = reactions.stream().collect(Collectors.groupingBy(Reaction::getEmoji));
        return byEmoji.entrySet().stream()
                .map(e -> new ReactionSummaryResponse(e.getKey(), e.getValue().size(),
                        e.getValue().stream().anyMatch(r -> r.getUserId().equals(viewerId))))
                .toList();
    }

    private List<Attachment> registerAttachments(UUID messageId, List<AttachmentRequest> requests) {
        List<Attachment> saved = new ArrayList<>();
        for (AttachmentRequest req : requests) {
            Attachment attachment = new Attachment();
            attachment.setMessageId(messageId);
            attachment.setFilename(req.filename());
            attachment.setMimeType(req.mimeType());
            attachment.setChecksum(req.checksum());
            attachment.setSizeBytes(req.sizeBytes());
            attachment.setStorageProvider(parseStorageProvider(req.storageProvider()));
            attachment.setStorageReference(req.storageReference());
            saved.add(attachmentRepository.save(attachment));
        }
        return saved;
    }

    private String buildPreview(Message message) {
        if (message.getType() == MessageType.SYSTEM) {
            return "[system] " + safeSubstring(message.getBody());
        }
        if (message.getBody() == null || message.getBody().isBlank()) {
            return "[" + message.getType().name().toLowerCase() + "]";
        }
        return safeSubstring(message.getBody());
    }

    private String safeSubstring(String body) {
        if (body == null) return "";
        return body.length() > PREVIEW_MAX_LENGTH ? body.substring(0, PREVIEW_MAX_LENGTH) + "..." : body;
    }

    private MessageType parseType(String raw) {
        try {
            return MessageType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown message type: " + raw);
        }
    }

    private StorageProvider parseStorageProvider(String raw) {
        try {
            return StorageProvider.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown storage provider: " + raw);
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private MessageResponse mapToResponse(Message message, List<Attachment> attachments,
                                           List<ReactionSummaryResponse> reactions, UUID viewerId) {
        List<AttachmentResponse> attachmentResponses = attachments.stream()
                .map(a -> new AttachmentResponse(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(),
                        a.getStorageProvider().name(), a.getStorageReference()))
                .toList();
        return new MessageResponse(
                message.getId(), message.getConversationId(), message.getSenderId(), message.getParentMessageId(),
                message.getType().name(), message.getStatus().name(), message.getBody(), readMetadata(message.getMetadataJson()),
                message.getReplyCount(), message.getLastReplyAt(), reactions, attachmentResponses,
                message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt()
        );
    }
}
