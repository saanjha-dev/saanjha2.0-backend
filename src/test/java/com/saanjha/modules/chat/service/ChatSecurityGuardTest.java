package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.MemberRole;
import com.saanjha.modules.chat.entity.MemberStatus;
import com.saanjha.modules.chat.entity.Message;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.team.service.TeamSecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression coverage in the same spirit as {@code TeamSecurityGuardTest}
 * (TD18/S12): proves membership/manager/sender checks distinguish "any
 * authenticated user" from "actually authorized for THIS resource", and
 * specifically that {@link ChatSecurityGuard#messageBelongsToConversation}
 * closes the exact TD25 class of bug (a child-resource id trusted without
 * verifying it belongs to the parent id also in the request) before it can
 * ever be introduced here.
 */
@ExtendWith(MockitoExtension.class)
class ChatSecurityGuardTest {

    @Mock private ConversationMemberRepository memberRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private TeamSecurityGuard teamSecurityGuard;

    private ChatSecurityGuard guard;

    private UUID conversationId;
    private UUID otherConversationId;
    private UUID memberUserId;
    private UUID strangerUserId;

    @BeforeEach
    void setUp() {
        guard = new ChatSecurityGuard(memberRepository, messageRepository, teamSecurityGuard);
        conversationId = UUID.randomUUID();
        otherConversationId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        strangerUserId = UUID.randomUUID();
    }

    @Test
    void isMember_returnsTrue_forALiveMember() {
        when(memberRepository.existsByConversationIdAndUserIdAndStatusIn(
                conversationId, memberUserId, List.of(MemberStatus.ACTIVE, MemberStatus.MUTED)))
                .thenReturn(true);

        assertThat(guard.isMember(conversationId, memberUserId.toString())).isTrue();
    }

    @Test
    void isMember_returnsFalse_forAStranger() {
        when(memberRepository.existsByConversationIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);

        assertThat(guard.isMember(conversationId, strangerUserId.toString())).isFalse();
    }

    @Test
    void isMember_returnsFalse_forMalformedUserId() {
        assertThat(guard.isMember(conversationId, "not-a-uuid")).isFalse();
    }

    @Test
    void canSend_returnsFalse_forAMutedMember() {
        ConversationMember muted = new ConversationMember();
        muted.setStatus(MemberStatus.MUTED);
        muted.setMutedUntil(java.time.Instant.now().plusSeconds(600));
        when(memberRepository.findByConversationIdAndUserId(conversationId, memberUserId))
                .thenReturn(Optional.of(muted));

        assertThat(guard.canSend(conversationId, memberUserId.toString())).isFalse();
    }

    @Test
    void isManager_returnsTrue_onlyForOwnerOrAdmin() {
        ConversationMember admin = new ConversationMember();
        admin.setStatus(MemberStatus.ACTIVE);
        admin.setRole(MemberRole.ADMIN);
        when(memberRepository.findByConversationIdAndUserId(conversationId, memberUserId))
                .thenReturn(Optional.of(admin));

        assertThat(guard.isManager(conversationId, memberUserId.toString())).isTrue();
    }

    @Test
    void isManager_returnsFalse_forAPlainMember() {
        ConversationMember member = new ConversationMember();
        member.setStatus(MemberStatus.ACTIVE);
        member.setRole(MemberRole.MEMBER);
        when(memberRepository.findByConversationIdAndUserId(conversationId, memberUserId))
                .thenReturn(Optional.of(member));

        assertThat(guard.isManager(conversationId, memberUserId.toString())).isFalse();
    }

    @Test
    void messageBelongsToConversation_returnsFalse_whenMessageBelongsToADifferentConversation() {
        // The TD25 regression case: a message that exists, and a conversation the caller is
        // authorized against, but the two don't actually belong together.
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.empty());

        assertThat(guard.messageBelongsToConversation(messageId, conversationId)).isFalse();
    }

    @Test
    void messageBelongsToConversation_returnsTrue_whenScopedCorrectly() {
        UUID messageId = UUID.randomUUID();
        Message message = new Message();
        message.setId(messageId);
        message.setConversationId(conversationId);
        when(messageRepository.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message));

        assertThat(guard.messageBelongsToConversation(messageId, conversationId)).isTrue();
    }

    @Test
    void isTeamMemberOfProject_delegatesToTeamSecurityGuard_true() {
        UUID projectId = UUID.randomUUID();
        when(teamSecurityGuard.isMemberOfProjectsTeam(projectId, memberUserId.toString())).thenReturn(true);

        assertThat(guard.isTeamMemberOfProject(projectId, memberUserId.toString())).isTrue();
    }

    @Test
    void isTeamMemberOfProject_delegatesToTeamSecurityGuard_false() {
        UUID projectId = UUID.randomUUID();
        when(teamSecurityGuard.isMemberOfProjectsTeam(projectId, strangerUserId.toString())).thenReturn(false);

        assertThat(guard.isTeamMemberOfProject(projectId, strangerUserId.toString())).isFalse();
    }
}
