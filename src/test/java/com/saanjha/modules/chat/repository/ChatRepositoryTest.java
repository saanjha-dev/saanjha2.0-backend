package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V24 migration's DB-level invariants against a real
 * PostgreSQL instance, same convention as {@code TeamRepositoryTest}: at
 * most one PROJECT_TEAM/PROJECT_ANNOUNCEMENTS conversation per project, one
 * membership row per (conversation, user), "unique per user" reactions, at
 * most one active pin per message, and the search_vector trigger actually
 * firing on insert (not something the application needs to populate itself).
 */
@DataJpaTest
@Testcontainers
class ChatRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationMemberRepository memberRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ReactionRepository reactionRepository;
    @Autowired private PinnedMessageRepository pinnedMessageRepository;

    @Test
    void projectConversationTypeUniqueness_blocksASecondProjectTeamChannel() {
        UUID projectId = UUID.randomUUID();
        conversationRepository.save(newConversation(projectId, ConversationType.PROJECT_TEAM));

        assertThatThrownBy(() -> {
            conversationRepository.save(newConversation(projectId, ConversationType.PROJECT_TEAM));
            conversationRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void projectConversationTypeUniqueness_allowsBothTeamAndAnnouncementsChannels() {
        UUID projectId = UUID.randomUUID();
        conversationRepository.save(newConversation(projectId, ConversationType.PROJECT_TEAM));
        conversationRepository.saveAndFlush(newConversation(projectId, ConversationType.PROJECT_ANNOUNCEMENTS));

        assertThat(conversationRepository.findByProjectId(projectId)).hasSize(2);
    }

    @Test
    void oneMembershipRowPerConversationAndUser_isEnforced() {
        Conversation conversation = conversationRepository.saveAndFlush(newConversation(null, ConversationType.GROUP));
        UUID userId = UUID.randomUUID();
        memberRepository.save(newMember(conversation.getId(), userId));

        assertThatThrownBy(() -> {
            memberRepository.save(newMember(conversation.getId(), userId));
            memberRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reactionUniquePerUserPerEmoji_isEnforced() {
        Conversation conversation = conversationRepository.saveAndFlush(newConversation(null, ConversationType.GROUP));
        Message message = messageRepository.saveAndFlush(newMessage(conversation.getId()));
        UUID userId = UUID.randomUUID();

        reactionRepository.saveAndFlush(newReaction(message.getId(), userId, "\uD83D\uDC4D"));

        assertThatThrownBy(() -> {
            reactionRepository.save(newReaction(message.getId(), userId, "\uD83D\uDC4D"));
            reactionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Different emoji, same user, same message - allowed.
        reactionRepository.saveAndFlush(newReaction(message.getId(), userId, "\uD83C\uDF89"));
        assertThat(reactionRepository.findByMessageId(message.getId())).hasSize(2);
    }

    @Test
    void atMostOneActivePinPerMessage_isEnforced() {
        Conversation conversation = conversationRepository.saveAndFlush(newConversation(null, ConversationType.GROUP));
        Message message = messageRepository.saveAndFlush(newMessage(conversation.getId()));

        PinnedMessage first = new PinnedMessage();
        first.setConversationId(conversation.getId());
        first.setMessageId(message.getId());
        first.setPinnedBy(UUID.randomUUID());
        pinnedMessageRepository.saveAndFlush(first);

        assertThatThrownBy(() -> {
            PinnedMessage second = new PinnedMessage();
            second.setConversationId(conversation.getId());
            second.setMessageId(message.getId());
            second.setPinnedBy(UUID.randomUUID());
            pinnedMessageRepository.save(second);
            pinnedMessageRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchVectorTrigger_populatesOnInsert_withoutApplicationInvolvement() {
        Conversation conversation = conversationRepository.saveAndFlush(newConversation(null, ConversationType.GROUP));
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(UUID.randomUUID());
        message.setType(MessageType.TEXT);
        message.setBody("the quick brown fox jumps over the lazy dog");
        messageRepository.saveAndFlush(message);

        var page = messageRepository.searchWithinConversation(conversation.getId(), "fox", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Message::getId).contains(message.getId());
    }

    private Conversation newConversation(UUID projectId, ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setProjectId(projectId);
        conversation.setType(type);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setSettingsJson("{}");
        return conversation;
    }

    private ConversationMember newMember(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }

    private Message newMessage(UUID conversationId) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());
        message.setType(MessageType.TEXT);
        message.setBody("hello");
        return message;
    }

    private Reaction newReaction(UUID messageId, UUID userId, String emoji) {
        Reaction reaction = new Reaction();
        reaction.setMessageId(messageId);
        reaction.setUserId(userId);
        reaction.setEmoji(emoji);
        return reaction;
    }
}
