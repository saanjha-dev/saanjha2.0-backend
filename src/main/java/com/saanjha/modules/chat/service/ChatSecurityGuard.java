package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.MemberRole;
import com.saanjha.modules.chat.entity.MemberStatus;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.team.service.TeamSecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resource-level authorization guard for the Chat module, following the same
 * {@code @xGuard} + {@code @PreAuthorize} composition pattern as {@code
 * TeamSecurityGuard} - a global PBAC permission (e.g. {@code
 * chat:participate}) is necessary but never sufficient; every endpoint also
 * checks membership in *this specific* conversation here.
 *
 * Learning applied from TD25 (technical-debt.md): every method that accepts
 * a child-resource id (messageId, membershipId) alongside a conversationId
 * re-verifies the child actually belongs to that conversation, rather than
 * trusting the path shape alone - the exact class of bug TD25 identified in
 * Team's {@code getMembershipHistory}.
 */
@Component("chatGuard")
@RequiredArgsConstructor
public class ChatSecurityGuard {

    private static final List<MemberStatus> LIVE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.MUTED);

    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final TeamSecurityGuard teamSecurityGuard;

    /**
     * P0-5 (Project Conversation Query): authorizes {@code GET /v1/projects/
     * {projectId}/conversations}. Composes {@code TeamSecurityGuard} for the
     * "is this user on this project's team" check rather than duplicating
     * membership logic — same reuse discipline {@code TaskSecurityGuard}
     * already established for the same cross-module question.
     */
    public boolean isTeamMemberOfProject(UUID projectId, String userIdText) {
        return teamSecurityGuard.isMemberOfProjectsTeam(projectId, userIdText);
    }

    /** True if the user currently holds a live (ACTIVE or MUTED) seat in this conversation. */
    public boolean isMember(UUID conversationId, String userIdText) {
        UUID userId = parseOrNull(userIdText);
        if (conversationId == null || userId == null) {
            return false;
        }
        return memberRepository.existsByConversationIdAndUserIdAndStatusIn(conversationId, userId, LIVE_STATUSES);
    }

    /** True if the user can currently send into this conversation: a live, non-muted member. */
    public boolean canSend(UUID conversationId, String userIdText) {
        UUID userId = parseOrNull(userIdText);
        if (conversationId == null || userId == null) {
            return false;
        }
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationMember::canSend)
                .orElse(false);
    }

    /** True if the user is OWNER or ADMIN of this conversation (i.e. holds {@code chat:manage}-eligible standing here). */
    public boolean isManager(UUID conversationId, String userIdText) {
        UUID userId = parseOrNull(userIdText);
        if (conversationId == null || userId == null) {
            return false;
        }
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE || m.getStatus() == MemberStatus.MUTED)
                .map(m -> m.getRole() == MemberRole.OWNER || m.getRole() == MemberRole.ADMIN)
                .orElse(false);
    }

    public boolean isOwner(UUID conversationId, String userIdText) {
        UUID userId = parseOrNull(userIdText);
        if (conversationId == null || userId == null) {
            return false;
        }
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(m -> m.getRole() == MemberRole.OWNER)
                .orElse(false);
    }

    /** True if the given user authored the message. Used for edit/delete-own-message checks. */
    public boolean isMessageSender(UUID messageId, String userIdText) {
        UUID userId = parseOrNull(userIdText);
        if (messageId == null || userId == null) {
            return false;
        }
        return messageRepository.findById(messageId)
                .map(m -> userId.equals(m.getSenderId()))
                .orElse(false);
    }

    /**
     * TD25-pattern guard: true only if {@code messageId} actually belongs to
     * {@code conversationId}. Every controller method that takes both ids
     * must call this (or the equivalent repository-level scoped query)
     * before trusting the message id at all - never assume the path's
     * conversationId and the payload's messageId agree just because a
     * membership check on conversationId passed.
     */
    public boolean messageBelongsToConversation(UUID messageId, UUID conversationId) {
        if (messageId == null || conversationId == null) {
            return false;
        }
        return messageRepository.findByIdAndConversationId(messageId, conversationId).isPresent();
    }

    private UUID parseOrNull(String text) {
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
