package com.saanjha.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ConversationSummaryResponse;
import com.saanjha.modules.chat.entity.Conversation;
import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.ConversationType;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.ConversationRepository;
import com.saanjha.modules.chat.repository.ModerationActionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.service.TeamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P0-5 (Project Conversation Query) regression coverage: proves {@code
 * listByProject} returns exactly the conversations linked to one project,
 * and that per-viewer unread counts are correctly attached from a single
 * batch lookup rather than a per-conversation round trip.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMemberRepository memberRepository;
    @Mock private ModerationActionRepository moderationActionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectService projectService;
    @Mock private TeamService teamService;
    @Mock private MembershipRepository teamMembershipRepository;

    private ConversationService conversationService;

    private UUID projectId;
    private UUID viewerId;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, memberRepository, moderationActionRepository,
                eventPublisher, new ObjectMapper(), new SimpleMeterRegistry(),
                projectService, teamService, teamMembershipRepository);
        projectId = UUID.randomUUID();
        viewerId = UUID.randomUUID();
    }

    @Test
    void listByProject_returnsOnlyThisProjectsConversations_withUnreadCountsAttached() {
        Conversation teamChat = projectConversation(ConversationType.PROJECT_TEAM, "Team Chat");
        Conversation announcements = projectConversation(ConversationType.PROJECT_ANNOUNCEMENTS, "Announcements");
        Pageable pageable = PageRequest.of(0, 20);

        when(conversationRepository.findByProjectId(projectId, pageable))
                .thenReturn(new PageImpl<>(List.of(teamChat, announcements)));

        ConversationMember unreadInTeamChat = new ConversationMember();
        unreadInTeamChat.setConversationId(teamChat.getId());
        unreadInTeamChat.setUserId(viewerId);
        unreadInTeamChat.setUnreadCount(4);

        when(memberRepository.findByConversationIdInAndUserId(
                eq(List.of(teamChat.getId(), announcements.getId())), eq(viewerId)))
                .thenReturn(List.of(unreadInTeamChat));

        Page<ConversationSummaryResponse> result = conversationService.listByProject(projectId, viewerId, pageable);

        assertThat(result.getContent()).hasSize(2);
        ConversationSummaryResponse teamChatSummary = result.getContent().stream()
                .filter(r -> r.id().equals(teamChat.getId())).findFirst().orElseThrow();
        ConversationSummaryResponse announcementsSummary = result.getContent().stream()
                .filter(r -> r.id().equals(announcements.getId())).findFirst().orElseThrow();

        assertThat(teamChatSummary.unreadCount()).isEqualTo(4);
        // No membership row returned for this one - defaults to 0, not an exception.
        assertThat(announcementsSummary.unreadCount()).isEqualTo(0);
    }

    @Test
    void listByProject_returnsEmptyPage_whenProjectHasNoConversationsYet() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.findByProjectId(projectId, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(memberRepository.findByConversationIdInAndUserId(eq(List.of()), eq(viewerId))).thenReturn(List.of());

        Page<ConversationSummaryResponse> result = conversationService.listByProject(projectId, viewerId, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    private Conversation projectConversation(ConversationType type, String name) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(projectId);
        conversation.setType(type);
        conversation.setName(name);
        conversation.setMemberCount(3);
        return conversation;
    }
}
