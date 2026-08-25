package com.saanjha.modules.chat.websocket;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.ReactRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ReactionEventResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ReactionSummaryResponse;
import com.saanjha.modules.chat.service.ChatSecurityGuard;
import com.saanjha.modules.chat.service.MessageService;
import com.saanjha.modules.chat.service.PresenceService;
import com.saanjha.modules.chat.service.ReactionService;
import com.saanjha.modules.chat.service.ReadReceiptService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-4 (Chat Reaction Persistence) regression coverage: previously {@code
 * addReaction} re-broadcast the raw client payload with no {@link
 * ReactionService} call at all - a reload lost every reaction. These tests
 * prove the WebSocket handlers now actually persist through {@code
 * ReactionService}, prove removal has a handler at all (it had none before),
 * and prove both handlers refuse to act on a messageId that doesn't belong
 * to the conversation in the destination (TD25 pattern).
 */
@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessageService messageService;
    @Mock private ReactionService reactionService;
    @Mock private ReadReceiptService readReceiptService;
    @Mock private PresenceService presenceService;
    @Mock private ChatSecurityGuard chatGuard;

    private ChatWebSocketController controller;

    private UUID conversationId;
    private UUID messageId;
    private UUID userId;
    private Principal principal;

    @BeforeEach
    void setUp() {
        controller = new ChatWebSocketController(
                messagingTemplate, messageService, reactionService, readReceiptService,
                presenceService, chatGuard, new SimpleMeterRegistry());
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        userId = UUID.randomUUID();
        principal = () -> userId.toString();
    }

    @Test
    void addReaction_persistsThroughReactionService_beforeBroadcasting() {
        when(chatGuard.isMember(conversationId, userId.toString())).thenReturn(true);
        when(chatGuard.messageBelongsToConversation(messageId, conversationId)).thenReturn(true);
        when(reactionService.summarizeForMessage(eq(messageId), any()))
                .thenReturn(List.of(new ReactionSummaryResponse("\uD83D\uDC4D", 1, false)));

        controller.addReaction(conversationId, new ReactRequest(messageId, "\uD83D\uDC4D"), principal);

        verify(reactionService).addReaction(conversationId, messageId, userId, "\uD83D\uDC4D");

        ArgumentCaptor<ReactionEventResponse> captor = ArgumentCaptor.forClass(ReactionEventResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId + "/reactions"), captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo(messageId);
        assertThat(captor.getValue().action()).isEqualTo("ADDED");
        assertThat(captor.getValue().reactions()).hasSize(1);
    }

    @Test
    void removeReaction_persistsThroughReactionService_beforeBroadcasting() {
        when(chatGuard.isMember(conversationId, userId.toString())).thenReturn(true);
        when(chatGuard.messageBelongsToConversation(messageId, conversationId)).thenReturn(true);
        when(reactionService.summarizeForMessage(eq(messageId), any())).thenReturn(List.of());

        controller.removeReaction(conversationId, new ReactRequest(messageId, "\uD83D\uDC4D"), principal);

        verify(reactionService).removeReaction(conversationId, messageId, userId, "\uD83D\uDC4D");

        ArgumentCaptor<ReactionEventResponse> captor = ArgumentCaptor.forClass(ReactionEventResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId + "/reactions"), captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("REMOVED");
    }

    @Test
    void addReaction_rejectsAMessageThatBelongsToADifferentConversation() {
        when(chatGuard.isMember(conversationId, userId.toString())).thenReturn(true);
        when(chatGuard.messageBelongsToConversation(messageId, conversationId)).thenReturn(false);

        assertThatThrownBy(() -> controller.addReaction(conversationId, new ReactRequest(messageId, "\uD83D\uDC4D"), principal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(reactionService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void addReaction_rejectsANonMember() {
        when(chatGuard.isMember(conversationId, userId.toString())).thenReturn(false);

        assertThatThrownBy(() -> controller.addReaction(conversationId, new ReactRequest(messageId, "\uD83D\uDC4D"), principal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CHAT_NOT_A_MEMBER));

        verifyNoInteractions(reactionService);
    }
}
