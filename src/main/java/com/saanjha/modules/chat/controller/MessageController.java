package com.saanjha.modules.chat.controller;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.EditMessageRequest;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.SearchRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ChatMutationResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.MessageResponse;
import com.saanjha.modules.chat.service.ChatSearchService;
import com.saanjha.modules.chat.service.ChatSecurityGuard;
import com.saanjha.modules.chat.service.MessageService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST owns history, thread reads, search, and edit/delete (module brief's
 * "WEBSOCKET" section reserves real-time message *delivery* for STOMP - see
 * {@code ChatWebSocketController#sendMessage} - but edit/delete are
 * infrequent mutations better served by a normal REST call with a
 * server-side broadcast, matching how Slack/Discord/Teams's own APIs split
 * "send" (socket-first) from "edit/delete" (REST-first, socket-broadcast)).
 * Every edit/delete broadcasts to the same {@code /topic/conversations/{id}/
 * messages} destination the WebSocket send path uses, so a subscribed
 * client sees both without special-casing the transport.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "11. Chat", description = "Message history, threads, and search")
public class MessageController {

    private final MessageService messageService;
    private final ChatSearchService chatSearchService;
    private final ChatSecurityGuard chatGuard;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/v1/chats/conversations/{conversationId}/messages")
    @RateLimit(action = "get-message-history", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Conversation History", description = "Cursor-paginated, newest first. Pass the oldest returned message's createdAt as the next cursor.")
    public ResponseEntity<ApiEnvelope<List<MessageResponse>>> getHistory(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        UUID viewerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiEnvelope.success(messageService.getHistory(conversationId, cursor, limit, viewerId)));
    }

    @GetMapping("/v1/chats/conversations/{conversationId}/messages/{messageId}/thread")
    @RateLimit(action = "get-thread-replies", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Thread Replies", description = "Cursor-paginated replies to a root message.")
    public ResponseEntity<ApiEnvelope<List<MessageResponse>>> getThread(
            @PathVariable UUID conversationId, @PathVariable UUID messageId,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        UUID viewerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiEnvelope.success(messageService.getThreadReplies(conversationId, messageId, cursor, limit, viewerId)));
    }

    @PatchMapping("/v1/chats/conversations/{conversationId}/messages/{messageId}")
    @RateLimit(action = "edit-message", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:participate') and (@chatGuard.isMessageSender(#messageId, authentication.name) or @chatGuard.isManager(#conversationId, authentication.name))")
    @Operation(summary = "Edit Message", description = "Own message only, unless the caller manages the conversation.")
    public ResponseEntity<ApiEnvelope<MessageResponse>> edit(
            @PathVariable UUID conversationId, @PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        requireScoped(messageId, conversationId);
        UUID editorId = SecurityUtils.getCurrentUserId();
        MessageResponse response = messageService.editMessage(conversationId, messageId, editorId, request.body());
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/messages", response);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @DeleteMapping("/v1/chats/conversations/{conversationId}/messages/{messageId}")
    @RateLimit(action = "delete-message", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:moderate') or (hasAuthority('chat:participate') and (@chatGuard.isMessageSender(#messageId, authentication.name) or @chatGuard.isManager(#conversationId, authentication.name)))")
    @Operation(summary = "Delete Message", description = "Soft-delete; own message, a conversation manager, or a global moderator.")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> delete(@PathVariable UUID conversationId, @PathVariable UUID messageId) {
        requireScoped(messageId, conversationId);
        UUID actorId = SecurityUtils.getCurrentUserId();
        boolean isModeratorAction = !chatGuard.isMessageSender(messageId, actorId.toString());
        messageService.deleteMessage(conversationId, messageId, actorId, isModeratorAction);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/messages",
                Map.of("messageId", messageId, "deleted", true));
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Message deleted.", "OK")));
    }

    @GetMapping("/v1/chats/conversations/{conversationId}/search")
    @RateLimit(action = "search-conversation", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Conversation-Scoped Search", description = "Full-text search within one conversation (GIN/tsvector).")
    public ResponseEntity<ApiEnvelope<List<MessageResponse>>> searchWithinConversation(
            @PathVariable UUID conversationId, @RequestParam String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID viewerId = SecurityUtils.getCurrentUserId();
        Page<MessageResponse> result = chatSearchService.searchWithinConversation(conversationId, query, viewerId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/chats/search")
    @RateLimit(action = "search-my-conversations", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:participate')")
    @Operation(summary = "Global Search", description = "Full-text search across every conversation the caller belongs to, with optional author and date-range filters.")
    public ResponseEntity<ApiEnvelope<List<MessageResponse>>> searchAcrossMine(
            @Valid @org.springframework.web.bind.annotation.ModelAttribute SearchRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID viewerId = SecurityUtils.getCurrentUserId();
        Page<MessageResponse> result = chatSearchService.searchAcrossMyConversations(viewerId, request, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /** TD25-pattern guard, applied from day one: a messageId in the path must
     * actually belong to the conversationId also in the path before any
     * further check (sender/manager) is even evaluated. */
    private void requireScoped(UUID messageId, UUID conversationId) {
        if (!chatGuard.messageBelongsToConversation(messageId, conversationId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation.");
        }
    }

    private Pageable buildPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize);
    }

    private Map<String, Object> paginationMeta(Page<?> page) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());
        return meta;
    }
}
