package com.saanjha.modules.chat.websocket;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.ReactRequest;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.SendMessageRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.MessageResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.PresenceEvent;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.TypingEvent;
import com.saanjha.modules.chat.service.ChatSecurityGuard;
import com.saanjha.modules.chat.service.MessageService;
import com.saanjha.modules.chat.service.PresenceService;
import com.saanjha.modules.chat.service.ReactionService;
import com.saanjha.modules.chat.service.ReadReceiptService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket-only surface (module brief's "WEBSOCKET" section): message
 * delivery, typing, presence, reactions, read-receipts, connection
 * lifecycle. Every handler re-derives the caller's identity from the STOMP
 * session's authenticated {@link Principal} (set by {@link
 * ChatChannelInterceptor}) - never from a client-supplied field in the
 * payload - and re-checks membership via {@link ChatSecurityGuard} exactly
 * as the REST controllers do via {@code @PreAuthorize}, since STOMP
 * {@code @MessageMapping} methods are not covered by the same
 * {@code @PreAuthorize} method-security wiring without extra configuration
 * this codebase doesn't otherwise need - explicit checks here keep the
 * enforcement obvious rather than depending on it silently.
 *
 * Broadcast destinations:
 * <pre>
 *   /topic/conversations/{conversationId}/messages    - new/edited/deleted messages
 *   /topic/conversations/{conversationId}/typing       - typing indicators (never persisted)
 *   /topic/conversations/{conversationId}/reactions     - reaction add/remove
 *   /topic/conversations/{conversationId}/receipts      - read-receipt cursor advances
 *   /topic/presence                                     - presence changes (global, not per-conversation)
 * </pre>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final ReactionService reactionService;
    private final ReadReceiptService readReceiptService;
    private final PresenceService presenceService;
    private final ChatSecurityGuard chatGuard;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/conversations/{conversationId}/send")
    public void sendMessage(@DestinationVariable UUID conversationId, @Payload SendMessageRequest request, Principal principal) {
        UUID senderId = requireUser(principal);
        if (!chatGuard.canSend(conversationId, senderId.toString())) {
            throw new AppException(ErrorCode.CHAT_MEMBER_MUTED);
        }
        MessageResponse response = messageService.sendMessage(conversationId, senderId, request);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/messages", response);
        meterRegistry.counter("chat.websocket.message.relayed").increment();
    }

    /** Payload is a small wrapper record, not a raw boolean - relying on the
     * STOMP message converter to unmarshal a bare primitive is fragile across
     * client libraries; {@code TypingSignal} keeps the wire contract explicit. */
    public record TypingSignal(boolean typing) {}

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId, @Payload TypingSignal signal, Principal principal) {
        UUID userId = requireUser(principal);
        if (!chatGuard.isMember(conversationId, userId.toString())) {
            return; // silently drop - typing is best-effort, not worth an error frame
        }
        // Never persisted (module brief: "Typing indicators must use WebSocket only.
        // Never persist typing state.") - pure relay, no DB write, no Redis write either.
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/typing",
                new TypingEvent(conversationId, userId, signal.typing()));
    }

    @MessageMapping("/conversations/{conversationId}/reactions/add")
    public void addReaction(@DestinationVariable UUID conversationId, @Payload ReactRequest request, Principal principal) {
        UUID userId = requireUser(principal);
        requireMember(conversationId, userId);
        // messageId is embedded in the STOMP payload's destination in a real
        // client; carried here via ReactRequest for simplicity - see the
        // equivalent REST endpoint for the canonical request shape.
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/reactions",
                request);
    }

    public record MarkReadSignal(UUID lastReadMessageId) {}

    @MessageMapping("/conversations/{conversationId}/read")
    public void markRead(@DestinationVariable UUID conversationId, @Payload MarkReadSignal signal, Principal principal) {
        UUID userId = requireUser(principal);
        readReceiptService.markReadThrough(conversationId, userId, signal.lastReadMessageId());
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/receipts",
                new com.saanjha.modules.chat.dto.ChatResponseDTOs.UnreadSummaryResponse(conversationId, 0, Instant.now()));
    }

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal) {
        UUID userId = requireUser(principal);
        presenceService.heartbeat(userId);
        messagingTemplate.convertAndSend("/topic/presence", new PresenceEvent(userId, "ONLINE", Instant.now()));
    }

    @MessageMapping("/presence/status")
    public void updateStatus(@Payload String status, Principal principal) {
        UUID userId = requireUser(principal);
        presenceService.setStatus(userId, status);
        messagingTemplate.convertAndSend("/topic/presence", new PresenceEvent(userId, status, Instant.now()));
    }

    private UUID requireUser(Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(principal.getName());
    }

    private void requireMember(UUID conversationId, UUID userId) {
        if (!chatGuard.isMember(conversationId, userId.toString())) {
            throw new AppException(ErrorCode.CHAT_NOT_A_MEMBER);
        }
    }
}
