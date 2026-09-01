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
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.modules.team.entity.Membership;
import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.service.TeamService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    // Cross-module read dependencies for role-channel sync
    private final ProjectService projectService;
    private final TeamService teamService;
    private final com.saanjha.modules.team.repository.MembershipRepository teamMembershipRepository;

    // -------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------

    @Transactional
    public ConversationResponse createConversation(UUID creatorId, CreateConversationRequest request) {
        ConversationType type = parseType(request.type());

        // FIX (Chat Rework): route DIRECT_MESSAGE through the idempotent,
        // race-safe get-or-create path instead of always inserting a new
        // row. This is the exact call NewMessageDialog already makes
        // (POST /v1/chats/conversations {type: DIRECT_MESSAGE,
        // memberUserIds:[userId]}), so existing frontend/API callers get
        // duplicate-free DMs with no contract change.
        if (type == ConversationType.DIRECT_MESSAGE) {
            if (request.memberUserIds().size() != 1) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "A direct conversation requires exactly one other member.");
            }
            return getOrCreateDirectConversation(creatorId, request.memberUserIds().get(0));
        }

        Conversation conversation = new Conversation();
        conversation.setType(type);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setName(request.name());
        conversation.setTopic(request.topic());
        // Link to project when provided (role-channel sync, workspace-created groups)
        if (request.projectId() != null) {
            conversation.setProjectId(request.projectId());
        }
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

    /**
     * Idempotent find-or-create for DIRECT_MESSAGE conversations (Chat
     * Rework root-cause fix - see V28's migration comment and
     * uq_conv_direct_pair). Same defensive shape as
     * {@link #getOrCreateProjectConversation}: try the read first, fall
     * back to a DB-level unique-index race loss on create rather than a
     * pre-check-then-insert TOCTOU window, so two concurrent requests for
     * the same pair (double-click, two tabs, retry-after-timeout) always
     * converge on one row.
     */
    @Transactional
    public ConversationResponse getOrCreateDirectConversation(UUID requesterId, UUID otherUserId) {
        if (requesterId.equals(otherUserId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot start a direct conversation with yourself.");
        }
        UUID low = requesterId.toString().compareTo(otherUserId.toString()) < 0 ? requesterId : otherUserId;
        UUID high = requesterId.toString().compareTo(otherUserId.toString()) < 0 ? otherUserId : requesterId;

        Optional<Conversation> existing =
                conversationRepository.findByTypeAndDirectUserLowAndDirectUserHigh(ConversationType.DIRECT_MESSAGE, low, high);

        Conversation conversation = existing.isPresent()
                ? reuseExistingDirectConversation(existing.get(), requesterId, otherUserId)
                : createDirectConversationRow(low, high, requesterId, otherUserId);

        return mapToResponse(conversation);
    }

    /**
     * A DM conversation already existed for this pair - reactivate either
     * side's membership if a previous {@code leaveConversation} left them
     * LEFT (so "message this person again" actually reopens it for both),
     * but never silently undo a BLOCKED status; that must go through the
     * explicit unblock flow.
     */
    private Conversation reuseExistingDirectConversation(Conversation conversation, UUID requesterId, UUID otherUserId) {
        reactivateMemberIfNeeded(conversation.getId(), requesterId);
        reactivateMemberIfNeeded(conversation.getId(), otherUserId);
        return conversation;
    }

    private void reactivateMemberIfNeeded(UUID conversationId, UUID userId) {
        memberRepository.findByConversationIdAndUserId(conversationId, userId).ifPresent(member -> {
            if (member.getStatus() == MemberStatus.BLOCKED) {
                throw new AppException(ErrorCode.CHAT_MEMBER_BLOCKED);
            }
            if (member.getStatus() != MemberStatus.ACTIVE && member.getStatus() != MemberStatus.MUTED) {
                member.setStatus(MemberStatus.ACTIVE);
                member.setLeftAt(null);
                memberRepository.save(member);
            }
        });
    }

    private Conversation createDirectConversationRow(UUID low, UUID high, UUID requesterId, UUID otherUserId) {
        try {
            Conversation conversation = new Conversation();
            conversation.setType(ConversationType.DIRECT_MESSAGE);
            conversation.setStatus(ConversationStatus.ACTIVE);
            conversation.setSettingsJson(writeSettings(ConversationSettings.defaults()));
            conversation.setDirectUserLow(low);
            conversation.setDirectUserHigh(high);
            conversation = conversationRepository.save(conversation);

            addMemberInternal(conversation.getId(), requesterId, MemberRole.MEMBER);
            addMemberInternal(conversation.getId(), otherUserId, MemberRole.MEMBER);
            conversation.setMemberCount(2);
            conversation = conversationRepository.save(conversation);

            meterRegistry.counter("chat.conversation.created", "type", ConversationType.DIRECT_MESSAGE.name()).increment();
            eventPublisher.publishEvent(new ConversationCreatedEvent(
                    conversation.getId(), ConversationType.DIRECT_MESSAGE.name(), null, null, requesterId, Instant.now()));
            return conversation;
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            // Another concurrent request for the same pair won uq_conv_direct_pair; use its row.
            return conversationRepository.findByTypeAndDirectUserLowAndDirectUserHigh(ConversationType.DIRECT_MESSAGE, low, high)
                    .orElseThrow(() -> raceLoser);
        }
    }

    // -------------------------------------------------------------------
    // Role-channel sync (Workspace Chat's dynamic channels)
    // -------------------------------------------------------------------

    /**
     * Idempotent find-or-create for project-scoped GROUP conversations.
     * Same defensive shape as {@link #getOrCreateProjectConversation}: read
     * first, fall back to DB-level constraint on create, so concurrent
     * callers (multiple clients opening the workspace at once) always
     * converge on one conversation per (project, name) pair.
     */
    @Transactional
    public Conversation getOrCreateProjectGroupConversation(UUID projectId, String name, UUID creatorId, List<UUID> memberUserIds) {
        Optional<Conversation> existing = conversationRepository.findFirstByProjectIdAndTypeAndName(projectId, ConversationType.GROUP, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            Conversation conversation = new Conversation();
            conversation.setProjectId(projectId);
            conversation.setType(ConversationType.GROUP);
            conversation.setStatus(ConversationStatus.ACTIVE);
            conversation.setName(name);
            conversation.setTopic("project:" + projectId + ":role:" + name);
            conversation.setSettingsJson(writeSettings(ConversationSettings.defaults()));
            conversation = conversationRepository.save(conversation);

            if (creatorId != null) {
                addMemberInternal(conversation.getId(), creatorId, MemberRole.OWNER);
            }
            for (UUID userId : memberUserIds) {
                if (!userId.equals(creatorId)) {
                    addMemberInternal(conversation.getId(), userId, MemberRole.MEMBER);
                }
            }
            conversation.setMemberCount((int) memberRepository.countByConversationIdAndStatusIn(
                    conversation.getId(), List.of(MemberStatus.ACTIVE, MemberStatus.MUTED)));
            conversation = conversationRepository.save(conversation);

            meterRegistry.counter("chat.conversation.created", "type", ConversationType.GROUP.name()).increment();
            eventPublisher.publishEvent(new ConversationCreatedEvent(
                    conversation.getId(), ConversationType.GROUP.name(), projectId, null, creatorId, Instant.now()));
            return conversation;
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            return conversationRepository.findFirstByProjectIdAndTypeAndName(projectId, ConversationType.GROUP, name)
                    .orElseThrow(() -> raceLoser);
        }
    }

    /**
     * Orchestrates role-channel sync for a project's workspace chat.
     * Reads the project's requirements (open roles) and team roster,
     * creates one GROUP conversation per role with the correct members,
     * and ensures the General channel exists for all members.
     *
     * Returns the full list of project conversations after sync.
     *
     * @param projectId      the project to sync
     * @param teamId         the team to read roster from (null if no team yet)
     * @param leadUserId     the project lead (always added to every channel)
     * @param roleNames      the project's open role names (from requirements)
     * @param rosterByRole   map of roleName → list of userIds assigned to that role
     * @param allMemberIds   all active team member user IDs
     */
    @Transactional
    public List<ConversationSummaryResponse> syncRoleChannels(
            UUID projectId, UUID teamId, UUID leadUserId,
            List<String> roleNames,
            Map<String, List<UUID>> rosterByRole,
            List<UUID> allMemberIds) {

        // 1. Ensure "General" GROUP channel with all members
        List<UUID> generalMembers = new java.util.ArrayList<>(allMemberIds);
        if (!generalMembers.contains(leadUserId)) {
            generalMembers.add(leadUserId);
        }
        Conversation generalChannel = getOrCreateProjectGroupConversation(projectId, "General", leadUserId, generalMembers);
        syncConversationMembers(generalChannel, leadUserId, generalMembers);

        // 1.5 Ensure "Announcements" channel with all members
        Conversation announcementsChannel = getOrCreateProjectConversation(projectId, teamId, ConversationType.PROJECT_ANNOUNCEMENTS, leadUserId);
        syncConversationMembers(announcementsChannel, leadUserId, generalMembers);

        // 2. For each open role, create/find a role channel
        for (String roleName : roleNames) {
            List<UUID> roleMembers = rosterByRole.getOrDefault(roleName, List.of());
            List<UUID> channelMembers = new java.util.ArrayList<>(roleMembers);
            // Always include the lead for oversight
            if (!channelMembers.contains(leadUserId)) {
                channelMembers.add(leadUserId);
            }
            Conversation roleChannel = getOrCreateProjectGroupConversation(projectId, roleName, leadUserId, channelMembers);
            syncConversationMembers(roleChannel, leadUserId, channelMembers);
        }

        // 3. Return the updated conversation list for this project
        Pageable all = Pageable.ofSize(100);
        Page<Conversation> conversations = conversationRepository.findByProjectId(projectId, all);
        return conversations.getContent().stream()
                .map(c -> new ConversationSummaryResponse(
                        c.getId(), c.getType().name(), c.getName(),
                        c.getMemberCount(), c.getLastMessageAt(),
                        c.getLastMessagePreview(), 0, null))
                .toList();
    }

    /**
     * Top-level orchestration called by the REST controller. Reads the
     * project's requirements (open roles) and team roster via cross-module
     * services, builds the roleName→memberUserIds mapping from each
     * member's {@code contributionTitle}, and delegates to
     * {@link #syncRoleChannels}.
     */
    @Transactional
    public List<ConversationSummaryResponse> syncRoleChannelsFromProject(UUID projectId, UUID callerId) {
        // Read project to get requirements (open roles) and leadUserId
        var project = projectService.getProject(projectId, callerId);
        UUID leadUserId = project.leadUserId();

        // Extract unique role names from project requirements
        List<String> roleNames = project.requirements().stream()
                .map(r -> r.roleName())
                .distinct()
                .toList();

        // Read team roster — need contributionTitle to map members to roles
        UUID teamId = null;
        Map<String, List<UUID>> rosterByRole = new java.util.HashMap<>();
        List<UUID> allMemberIds = new java.util.ArrayList<>();

        try {
            var teamResponse = teamService.getTeamByProject(projectId);
            teamId = teamResponse.id();
            // Get all active memberships with contributionTitle
            List<Membership> activeMemberships = teamMembershipRepository
                    .findByTeam_IdAndStatusIn(teamResponse.id(),
                            List.of(com.saanjha.modules.team.entity.MembershipStatus.ACTIVE));
            for (Membership m : activeMemberships) {
                allMemberIds.add(m.getUserId());
                if (m.getContributionTitle() != null && !m.getContributionTitle().isBlank()) {
                    rosterByRole.computeIfAbsent(m.getContributionTitle(), k -> new java.util.ArrayList<>())
                            .add(m.getUserId());
                }
            }
        } catch (Exception ex) {
            // Team may not exist yet — proceed with empty roster
        }

        return syncRoleChannels(projectId, teamId, leadUserId, roleNames, rosterByRole, allMemberIds);
    }

    private void syncConversationMembers(Conversation conversation, UUID ownerId, List<UUID> targetMemberIds) {
        List<ConversationMember> currentMembers = memberRepository.findByConversationId(conversation.getId());
        java.util.Set<UUID> targetIds = new java.util.HashSet<>(targetMemberIds);
        if (ownerId != null) {
            targetIds.add(ownerId);
        }

        // Add or reactivate missing members
        for (UUID targetId : targetIds) {
            Optional<ConversationMember> existing = currentMembers.stream()
                    .filter(m -> m.getUserId().equals(targetId))
                    .findFirst();
            if (existing.isPresent()) {
                ConversationMember m = existing.get();
                if (m.getStatus() != MemberStatus.ACTIVE && m.getStatus() != MemberStatus.MUTED && m.getStatus() != MemberStatus.BLOCKED) {
                    m.setStatus(MemberStatus.ACTIVE);
                    memberRepository.save(m);
                }
            } else {
                addMemberInternal(conversation.getId(), targetId, targetId.equals(ownerId) ? MemberRole.OWNER : MemberRole.MEMBER);
            }
        }

        // Remove extra members
        for (ConversationMember m : currentMembers) {
            if (!targetIds.contains(m.getUserId()) && m.getStatus() == MemberStatus.ACTIVE) {
                m.setStatus(MemberStatus.REMOVED);
                memberRepository.save(m);
            }
        }

        conversation.setMemberCount((int) memberRepository.countByConversationIdAndStatusIn(
                conversation.getId(), List.of(MemberStatus.ACTIVE, MemberStatus.MUTED)));
        conversationRepository.save(conversation);
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
    public void clearHistory(UUID conversationId, UUID userId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Member not found in this conversation."));
        member.setClearedAt(Instant.now());
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Instant getClearedAt(UUID conversationId, UUID userId) {
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationMember::getClearedAt)
                .orElse(null);
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
        return conversationRepository.findGlobalConversationsForUser(userId, live, pageable)
                .map(conversation -> {
                    ConversationMember member = memberRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Member not found"));
                    
                    UUID peerId = null;
                    if (conversation.getType() == ConversationType.DIRECT_MESSAGE) {
                        peerId = memberRepository.findByConversationId(conversation.getId()).stream()
                            .map(ConversationMember::getUserId)
                            .filter(id -> !id.equals(userId))
                            .findFirst().orElse(null);
                    }
                            
                    return new ConversationSummaryResponse(
                            conversation.getId(), conversation.getType().name(), conversation.getName(),
                            conversation.getMemberCount(), conversation.getLastMessageAt(),
                            conversation.getLastMessagePreview(), member.getUnreadCount(), peerId);
                });
    }

    /**
     * FIX (P0-5, Project Conversation Query): previously the frontend had no
     * way to ask "give me this project's conversations" directly - only
     * {@code listMyConversations} (every conversation the caller belongs to,
     * unfiltered) or the internal, unpaginated {@code findByProjectId(UUID)}
     * used by archival/locking sweeps. Batch-loads the viewer's membership
     * rows for the whole page in one query (not per-conversation) to compute
     * {@code unreadCount} without an N+1.
     */
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listByProject(UUID projectId, UUID viewerId, Pageable pageable) {
        Page<Conversation> conversations = conversationRepository.findByProjectId(projectId, pageable);
        List<UUID> conversationIds = conversations.getContent().stream().map(Conversation::getId).toList();

        Map<UUID, Integer> unreadByConversation = memberRepository.findByConversationIdInAndUserId(conversationIds, viewerId)
                .stream()
                .collect(Collectors.toMap(ConversationMember::getConversationId, ConversationMember::getUnreadCount));

        return conversations.map(conversation -> {
            UUID peerId = null;
            if (conversation.getType() == ConversationType.DIRECT_MESSAGE) {
                peerId = memberRepository.findByConversationId(conversation.getId()).stream()
                    .map(ConversationMember::getUserId)
                    .filter(id -> !id.equals(viewerId))
                    .findFirst().orElse(null);
            }
            return new ConversationSummaryResponse(
                conversation.getId(), conversation.getType().name(), conversation.getName(),
                conversation.getMemberCount(), conversation.getLastMessageAt(),
                conversation.getLastMessagePreview(), unreadByConversation.getOrDefault(conversation.getId(), 0), peerId);
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
