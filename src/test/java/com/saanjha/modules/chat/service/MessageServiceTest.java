package com.saanjha.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.SendMessageRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.MessageResponse;
import com.saanjha.modules.chat.entity.*;
import com.saanjha.modules.chat.repository.*;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private ConversationService conversationService;
    @Mock private ReadReceiptService readReceiptService;
    @Mock private MentionService mentionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private MessageService messageService;

    private UUID conversationId;
    private UUID senderId;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository, conversationRepository, attachmentRepository, reactionRepository,
                conversationService, readReceiptService, mentionService, eventPublisher,
                new ObjectMapper(), new SimpleMeterRegistry());
        conversationId = UUID.randomUUID();
        senderId = UUID.randomUUID();
    }

    @Test
    void sendMessage_rejectsSend_whenConversationIsArchived() {
        Conversation archived = new Conversation();
        archived.setStatus(ConversationStatus.ARCHIVED);
        when(conversationRepository.findWithLockById(conversationId)).thenReturn(Optional.of(archived));

        SendMessageRequest request = new SendMessageRequest("TEXT", "hello", null, Map.of(), List.of());

        assertThatThrownBy(() -> messageService.sendMessage(conversationId, senderId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_CONVERSATION_READ_ONLY);
    }

    @Test
    void sendMessage_rejectsReplyToAReply_toKeepThreadsOneLevelDeep() {
        Conversation active = activeConversation();
        when(conversationRepository.findWithLockById(conversationId)).thenReturn(Optional.of(active));

        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        Message replyMessage = new Message();
        replyMessage.setId(replyId);
        replyMessage.setParentMessageId(rootId); // this message is itself a reply, not a root
        when(messageRepository.findByIdAndConversationId(replyId, conversationId)).thenReturn(Optional.of(replyMessage));

        SendMessageRequest request = new SendMessageRequest("TEXT", "a reply to a reply", replyId, Map.of(), List.of());

        assertThatThrownBy(() -> messageService.sendMessage(conversationId, senderId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_THREAD_DEPTH_EXCEEDED);
    }

    @Test
    void sendMessage_succeeds_andIncrementsConversationCounters() {
        Conversation active = activeConversation();
        when(conversationRepository.findWithLockById(conversationId)).thenReturn(Optional.of(active));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
//        when(reactionRepository.findByMessageId(any())).thenReturn(List.of());

        SendMessageRequest request = new SendMessageRequest("TEXT", "hello team", null, Map.of(), List.of());
        MessageResponse response = messageService.sendMessage(conversationId, senderId, request);

        assertThat(response.body()).isEqualTo("hello team");
        assertThat(response.senderId()).isEqualTo(senderId);
        assertThat(active.getMessageCount()).isEqualTo(1);
        verify(readReceiptService).incrementUnreadForOthers(conversationId, senderId);
        verify(mentionService).processMentions(any(), eq(conversationId), eq(senderId), eq("hello team"));
        verify(eventPublisher).publishEvent(any(com.saanjha.modules.chat.event.ChatEvents.MessageSentEvent.class));
    }

    @Test
    void deleteMessage_isIdempotent_forAnAlreadyDeletedMessage() {
        Message deleted = new Message();
        deleted.setId(UUID.randomUUID());
        deleted.setConversationId(conversationId);
        deleted.setStatus(MessageStatus.DELETED);
        when(messageRepository.findByIdAndConversationId(deleted.getId(), conversationId)).thenReturn(Optional.of(deleted));

        messageService.deleteMessage(conversationId, deleted.getId(), senderId, false);

        verify(messageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(com.saanjha.modules.chat.event.ChatEvents.MessageDeletedEvent.class));
    }

    @Test
    void deleteMessage_recordsModeration_onlyWhenActingAsModerator() {
        UUID messageId = UUID.randomUUID();
        UUID originalSenderId = UUID.randomUUID();
        Message message = new Message();
        message.setId(messageId);
        message.setConversationId(conversationId);
        message.setSenderId(originalSenderId);
        message.setStatus(MessageStatus.SENT);
        when(messageRepository.findByIdAndConversationId(messageId, conversationId)).thenReturn(Optional.of(message));

        UUID moderatorId = UUID.randomUUID();
        messageService.deleteMessage(conversationId, messageId, moderatorId, true);

        verify(conversationService).recordModeration(
                eq(conversationId), eq(messageId), eq(originalSenderId), eq(ModerationActionType.DELETE_MESSAGE), any(), eq(moderatorId));
        assertThat(message.isDeleted()).isTrue();
        assertThat(message.getBody()).isNull();
    }

    private Conversation activeConversation() {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setStatus(ConversationStatus.ACTIVE);
        return conversation;
    }
}
