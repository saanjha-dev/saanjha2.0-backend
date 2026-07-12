package com.saanjha.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.CreateConversationRequest;
import com.saanjha.modules.chat.dto.ChatRequestDTOs.UpdateConversationSettingsRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ConversationMemberResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ConversationResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ConversationSettingsResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ConversationSummaryResponse;
import com.saanjha.modules.chat.entity.*;
import com.saanjha.modules.chat.event.ChatEvents.ConversationArchivedEvent;
import com.saanjha.modules.chat.event.ChatEvents.ConversationCreatedEvent;
import com.saanjha.modules.chat.event.ChatEvents.ConversationLockedEvent;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.ConversationRepository;
import com.saanjha.modules.chat.repository.ModerationActionRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the Conversation aggregate root and its roster (ConversationMember).
 * Message content itself lives in {@link MessageService} - this class never
 * touches cht_messages directly except to read denormalized counters it
 * itself maintains.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------

    @Transactional
    public ConversationResponse createConversation(UUID creatorId, CreateConversationRequest request) {
        ConversationType type = parseType(request.type());

        Conversation conversation = new Conversation();
        conversation.setType(type);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setName(request.name());
        conversation.setTopic(request.topic());
        conversation.setSettingsJson(writeSettings(ConversationSettings.defaults()));
        conversation = conversationRepository.save(conversation);

        addMemberInternal(conversation.getId(), creatorId, MemberRole.OWNER);
        for (UUID userId : request.memberUserIds()) {
            if (!userId.equals(creatorId)) {
                addMemberInternal(conversation.getId(), userId, MemberRole.MEMBER);
            }
        }
        conversation.setMemberCount((int) memberRepository.countByConversationIdAndStatusIn(
                conversation.getId(), List.of(MemberStatus.ACTIVE, MemberStatus.MUTED)));
        conversation = conversationRepository.save(conversation);

        meterRegistry.counter("chat.conversation.created", "type", type.name()).increment();
        eventPublisher.publishEvent(new ConversationCreatedEvent(
                conversation.getId(), type.name(), null, null, creatorId, Instant.now()));

        return mapToResponse(conversation);
    }

    /**
     * Idempotent find-or-create for auto-provisioned project conversations
     * (module brief's "AUTO PROVISIONING" section). Guarded at the DB level
     * by {@code uq_conv_project_type} - a racing duplicate event delivery
     * loses the unique index, not the application-level check, exactly the
     * same defensive posture Team's {@code getOrCreateTeam} takes against
     * ProjectPublishedEvent redelivery.
     */
    @Transactional
    public Conversation getOrCreateProjectConversation(UUID projectId, UUID teamId, ConversationType type, UUID founderUserId) {
        Optional<Conversation> existing = conversationRepository.findByProjectIdAndType(projectId, type);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            Conversation conversation = new Conversation();
            conversation.setProjectId(projectId);
            conversation.setTeamId(teamId);
            conversation.setType(type);
            conversation.setStatus(ConversationStatus.ACTIVE);
            conversation.setName(type == ConversationType.PROJECT_ANNOUNCEMENTS ? "Announcements" : "Team Chat");
            ConversationSettings settings = type == ConversationType.PROJECT_ANNOUNCEMENTS
                    ? ConversationSettings.announcementsDefaults() : ConversationSettings.defaults();
            conversation.setSettingsJson(writeSettings(settings));
            conversation = conversationRepository.save(conversation);

            if (founderUserId != null) {
                addMemberInternal(conversation.getId(), founderUserId, MemberRole.OWNER);
                conversation.setMemberCount(1);
                conversation = conversationRepository.save(conversation);
            }
            meterRegistry.counter("chat.conversation.created", "type", type.name()).increment();
            eventPublisher.publishEvent(new ConversationCreatedEvent(
                    conversation.getId(), type.name(), projectId, teamId, founderUserId, Instant.now()));
            return conversation;
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            // Another concurrent listener delivery won the unique-index race; use its row.
            return conversationRepository.findByProjectIdAndType(projectId, type)
                    .orElseThrow(() -> raceLoser);
        }
    }

    // -------------------------------------------------------------------
    // Membership
    // -------------------------------------------------------------------

    @Transactional
    public void addMember(UUID conversationId, UUID userId) {
        Conversation conversation = getActiveOrThrow(conversationId);
        if (!conversation.acceptsNewMembers()) {
            throw new AppException(ErrorCode.CHAT_CONVERSATION_READ_ONLY);
        }
        Optional<ConversationMember> existing = memberRepository.findByConversationIdAndUserId(conversationId, userId);
        if (existing.isPresent() && existing.get().getStatus() == MemberStatus.BLOCKED) {
            throw new AppException(ErrorCode.CHAT_MEMBER_BLOCKED);
        }
        boolean wasNew = existing.isEmpty();
        addMemberInternal(conversationId, userId, MemberRole.MEMBER);
        if (wasNew) {
            conversation.setMemberCount(conversation.getMemberCount() + 1);
            conversationRepository.save(conversation);
        }
    }

    private void addMemberInternal(UUID conversationId, UUID userId, MemberRole role) {
        Optional<ConversationMember> existing = memberRepository.findByConversationIdAndUserId(conversationId, userId);
        if (existing.isPresent()) {
            ConversationMember member = existing.get();
            member.setStatus(MemberStatus.ACTIVE);
            member.setLeftAt(null);
            member.setRemovedBy(null);
            member.setRemovalReason(null);
            memberRepository.save(member);
            return;
        }
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());
        memberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID conversationId, UUID targetUserId, UUID actingUserId, String reason) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "This user is not a member of the conversation."));
        member.setStatus(MemberStatus.REMOVED);
        member.setLeftAt(Instant.now());
        member.setRemovedBy(actingUserId);
        member.setRemovalReason(reason);
        memberRepository.save(member);
        decrementMemberCount(conversationId);
        recordModeration(conversationId, null, targetUserId, ModerationActionType.REMOVE_MEMBER, reason, actingUserId);
    }

    @Transactional
    public void leaveConversation(UUID conversationId, UUID userId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_NOT_A_MEMBER));
        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        memberRepository.save(member);
        decrementMemberCount(conversationId);
    }

    @Transactional
    public void muteMember(UUID conversationId, UUID targetUserId, UUID actingUserId, int durationMinutes, String reason) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "This user is not a member of the conversation."));
        member.setMutedUntil(Instant.now().plusSeconds(durationMinutes * 60L));
        memberRepository.save(member);
        recordModeration(conversationId, null, targetUserId, ModerationActionType.MUTE_MEMBER, reason, actingUserId);
    }

    @Transactional
    public void blockUser(UUID conversationId, UUID targetUserId, UUID actingUserId, String reason) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseGet(() -> {
                    ConversationMember m = new ConversationMember();
                    m.setConversationId(conversationId);
                    m.setUserId(targetUserId);
                    m.setRole(MemberRole.MEMBER);
                    return m;
                });
        boolean wasLive = member.getId() != null && member.isLive();
        member.setStatus(MemberStatus.BLOCKED);
        member.setLeftAt(Instant.now());
        memberRepository.save(member);
        if (wasLive) {
            decrementMemberCount(conversationId);
        }
        recordModeration(conversationId, null, targetUserId, ModerationActionType.BLOCK_USER, reason, actingUserId);
    }

    @Transactional
    public void unblockUser(UUID conversationId, UUID targetUserId, UUID actingUserId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.BLOCKED)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "This user is not currently blocked."));
        member.setStatus(MemberStatus.REMOVED);
        memberRepository.save(member);
        recordModeration(conversationId, null, targetUserId, ModerationActionType.UNBLOCK_USER, null, actingUserId);
    }

    private void decrementMemberCount(UUID conversationId) {
        Conversation conversation = conversationRepository.findWithLockById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
        conversation.setMemberCount(Math.max(0, conversation.getMemberCount() - 1));
        conversationRepository.save(conversation);
    }

    // -------------------------------------------------------------------
    // Settings / lifecycle
    // -------------------------------------------------------------------

    @Transactional
    public ConversationResponse updateSettings(UUID conversationId, UpdateConversationSettingsRequest request) {
        Conversation conversation = getActiveOrThrow(conversationId);
        ConversationSettings current = readSettings(conversation.getSettingsJson());
        ConversationSettings updated = new ConversationSettings(
                request.onlyAdminsCanPost() != null ? request.onlyAdminsCanPost() : current.onlyAdminsCanPost(),
                request.allowThreads() != null ? request.allowThreads() : current.allowThreads(),
                request.allowReactions() != null ? request.allowReactions() : current.allowReactions(),
                request.slowModeSeconds() != null ? request.slowModeSeconds() : current.slowModeSeconds(),
                request.allowExternalReferences() != null ? request.allowExternalReferences() : current.allowExternalReferences()
        );
        conversation.setSettingsJson(writeSettings(updated));
        return mapToResponse(conversationRepository.save(conversation));
    }

    @Transactional
    public void lockConversation(UUID conversationId, UUID actorId, String reason) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
        conversation.setStatus(ConversationStatus.LOCKED);
        conversation.setLockedAt(Instant.now());
        conversationRepository.save(conversation);
        recordModeration(conversationId, null, null, ModerationActionType.LOCK_CONVERSATION, reason, actorId);
        eventPublisher.publishEvent(new ConversationLockedEvent(conversationId, reason, Instant.now()));
    }

    @Transactional
    public void unlockConversation(UUID conversationId, UUID actorId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setLockedAt(null);
        conversationRepository.save(conversation);
        recordModeration(conversationId, null, null, ModerationActionType.UNLOCK_CONVERSATION, null, actorId);
    }

    @Transactional
    public void archiveConversation(UUID conversationId, String reason) {
        Optional<Conversation> maybe = conversationRepository.findById(conversationId);
        if (maybe.isEmpty()) {
            return; // idempotent no-op - conversation may never have been provisioned for this project
        }
        Conversation conversation = maybe.get();
        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            return;
        }
        conversation.setStatus(ConversationStatus.ARCHIVED);
        conversation.setArchivedAt(Instant.now());
        conversationRepository.save(conversation);
        eventPublisher.publishEvent(new ConversationArchivedEvent(conversationId, reason, Instant.now()));
    }

    @Transactional
    public void archiveAllForProject(UUID projectId, String reason) {
        for (Conversation conversation : conversationRepository.findByProjectId(projectId)) {
            archiveConversation(conversation.getId(), reason);
        }
    }

    @Transactional
    public void lockAllForProject(UUID projectId, UUID actorId, String reason) {
        for (Conversation conversation : conversationRepository.findByProjectId(projectId)) {
            if (conversation.getStatus() == ConversationStatus.ACTIVE) {
                lockConversation(conversation.getId(), actorId, reason);
            }
        }
    }

    // -------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId) {
        return mapToResponse(getActiveOrArchivedOrThrow(conversationId));
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listMyConversations(UUID userId, Pageable pageable) {
        List<MemberStatus> live = List.of(MemberStatus.ACTIVE, MemberStatus.MUTED);
        return memberRepository.findByUserIdAndStatusIn(userId, live, pageable)
                .map(member -> {
                    Conversation conversation = conversationRepository.findById(member.getConversationId())
                            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
                    return new ConversationSummaryResponse(
                            conversation.getId(), conversation.getType().name(), conversation.getName(),
                            conversation.getMemberCount(), conversation.getLastMessageAt(),
                            conversation.getLastMessagePreview(), member.getUnreadCount());
                });
    }

    @Transactional(readOnly = true)
    public Page<ConversationMemberResponse> listMembers(UUID conversationId, Pageable pageable) {
        return memberRepository.findByConversationIdAndStatusIn(
                conversationId, List.of(MemberStatus.ACTIVE, MemberStatus.MUTED), pageable
        ).map(this::mapMemberToResponse);
    }

    // -------------------------------------------------------------------
    // Package-private helpers used by other Chat services
    // -------------------------------------------------------------------

    Conversation getActiveOrThrow(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new AppException(ErrorCode.CHAT_CONVERSATION_READ_ONLY);
        }
        return conversation;
    }

    Conversation getActiveOrArchivedOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Conversation not found."));
    }

    ConversationSettings readSettings(String json) {
        try {
            if (json == null || json.isBlank() || json.equals("{}")) {
                return ConversationSettings.defaults();
            }
            return objectMapper.readValue(json, ConversationSettings.class);
        } catch (Exception ex) {
            return ConversationSettings.defaults();
        }
    }

    private String writeSettings(ConversationSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize conversation settings.");
        }
    }

    void recordModeration(UUID conversationId, UUID messageId, UUID targetUserId,
                           ModerationActionType type, String reason, UUID actorId) {
        ModerationAction action = new ModerationAction();
        action.setConversationId(conversationId);
        action.setMessageId(messageId);
        action.setTargetUserId(targetUserId);
        action.setActionType(type);
        action.setReason(reason);
        action.setActorId(actorId != null ? actorId : ModerationAction.SYSTEM_ACTOR_ID);
        moderationActionRepository.save(action);
    }

    private ConversationType parseType(String raw) {
        try {
            return ConversationType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown conversation type: " + raw);
        }
    }

    private ConversationResponse mapToResponse(Conversation conversation) {
        ConversationSettings settings = readSettings(conversation.getSettingsJson());
        return new ConversationResponse(
                conversation.getId(), conversation.getProjectId(), conversation.getTeamId(),
                conversation.getType().name(), conversation.getStatus().name(),
                conversation.getName(), conversation.getTopic(),
                new ConversationSettingsResponse(settings.onlyAdminsCanPost(), settings.allowThreads(),
                        settings.allowReactions(), settings.slowModeSeconds(), settings.allowExternalReferences()),
                conversation.getMemberCount(), conversation.getMessageCount(),
                conversation.getLastMessageAt(), conversation.getLastMessagePreview(),
                conversation.getCreatedAt()
        );
    }

    private ConversationMemberResponse mapMemberToResponse(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getId(), member.getUserId(), member.getRole().name(), member.getStatus().name(),
                member.getUnreadCount(), member.getLastReadAt(), member.getMutedUntil(), member.getJoinedAt()
        );
    }
}
