package com.saanjha.modules.chat.event;

import com.saanjha.modules.chat.entity.Conversation;
import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.ConversationType;
import com.saanjha.modules.chat.entity.MemberStatus;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.ConversationRepository;
import com.saanjha.modules.chat.service.ConversationService;
import com.saanjha.modules.chat.service.MessageService;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.team.event.TeamEvents.MemberJoinedEvent;
import com.saanjha.modules.team.event.TeamEvents.MemberLeftEvent;
import com.saanjha.modules.team.event.TeamEvents.TeamCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the module's inbound event contract: auto-provisioning on {@code
 * TeamCreatedEvent}, roster sync on member join/leave, and - the most
 * important property for a defensive consumer - that a handler throwing
 * never propagates (the {@code safely()} wrapper's whole purpose) so one
 * bad event can never take down delivery of a sibling handler or roll back
 * the producing module's already-committed transaction.
 */
@ExtendWith(MockitoExtension.class)
class ChatModuleEventListenerTest {

    @Mock private ConversationService conversationService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMemberRepository memberRepository;
    @Mock private MessageService messageService;

    private ChatModuleEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatModuleEventListener(conversationService, conversationRepository, memberRepository, messageService);
    }

    @Test
    void onTeamCreated_provisionsBothDefaultChannels() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();

        Conversation teamChat = new Conversation();
        teamChat.setId(UUID.randomUUID());
        Conversation announcements = new Conversation();
        announcements.setId(UUID.randomUUID());

        when(conversationService.getOrCreateProjectConversation(projectId, teamId, ConversationType.PROJECT_TEAM, founderId))
                .thenReturn(teamChat);
        when(conversationService.getOrCreateProjectConversation(projectId, teamId, ConversationType.PROJECT_ANNOUNCEMENTS, founderId))
                .thenReturn(announcements);

        listener.onTeamCreated(new TeamCreatedEvent(teamId, projectId, founderId, Instant.now()));

        verify(messageService).postSystemMessage(eq(teamChat.getId()), any(), any());
        verify(messageService).postSystemMessage(eq(announcements.getId()), any(), any());
    }

    @Test
    void onMemberJoined_addsMemberToEveryProjectConversation() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation teamChat = new Conversation();
        teamChat.setId(UUID.randomUUID());
        when(conversationRepository.findByProjectId(projectId)).thenReturn(List.of(teamChat));

        listener.onMemberJoined(new MemberJoinedEvent(UUID.randomUUID(), projectId, UUID.randomUUID(), userId, "APPLICATION", 3, Instant.now()));

        verify(conversationService).addMember(teamChat.getId(), userId);
        verify(messageService).postSystemMessage(eq(teamChat.getId()), any(), any());
    }

    @Test
    void onMemberLeft_isIdempotent_whenAlreadyRemovedByAPriorDelivery() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation teamChat = new Conversation();
        teamChat.setId(UUID.randomUUID());
        when(conversationRepository.findByProjectId(projectId)).thenReturn(List.of(teamChat));

        ConversationMember alreadyLeft = new ConversationMember();
        alreadyLeft.setStatus(MemberStatus.LEFT);
        when(memberRepository.findByConversationIdAndUserId(teamChat.getId(), userId)).thenReturn(Optional.of(alreadyLeft));

        listener.onMemberLeft(new MemberLeftEvent(UUID.randomUUID(), projectId, UUID.randomUUID(), userId, 2, Instant.now()));

        // Redelivery after the member is already LEFT must not call leaveConversation again.
        verify(conversationService, never()).leaveConversation(any(), any());
    }

    @Test
    void aHandlerThrowing_neverPropagates_soASiblingHandlersProcessingIsUnaffected() {
        UUID projectId = UUID.randomUUID();
        when(conversationRepository.findByProjectId(projectId)).thenThrow(new RuntimeException("simulated DB blip"));

        assertThatCode(() -> listener.onProjectCompleted(new ProjectCompletedEvent(projectId, UUID.randomUUID(), Instant.now())))
                .doesNotThrowAnyException();
    }
}
