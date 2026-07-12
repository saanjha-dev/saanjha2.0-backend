package com.saanjha.modules.chat.event;

import com.saanjha.modules.chat.entity.Conversation;
import com.saanjha.modules.chat.entity.ConversationType;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.ConversationRepository;
import com.saanjha.modules.chat.service.ConversationService;
import com.saanjha.modules.chat.service.MessageService;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionMilestoneReachedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.task.event.TaskEvents.TaskAssignedEvent;
import com.saanjha.modules.task.event.TaskEvents.TaskCompletedEvent;
import com.saanjha.modules.team.event.TeamEvents.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat's inbound event contract. Every handler follows the codebase-wide
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code safely()}
 * convention (see {@code TeamEventListener}) so a delivery failure here
 * never rolls back the producing module's own transaction, and every
 * handler treats redelivery as a safe no-op rather than trusting
 * exactly-once delivery.
 *
 * Auto-provisioning contract (module brief's "AUTO PROVISIONING" section),
 * with one deliberate deviation from the brief's literal event mapping,
 * documented inline below: Chat keys default-channel creation off {@code
 * TeamCreatedEvent}, not {@code ProjectPublishedEvent}. Team is already the
 * canonical "does a team exist for this project yet" signal in this
 * codebase (module-health.md), and {@code TeamCreatedEvent} carries {@code
 * teamId} directly - listening to ProjectPublishedEvent instead would mean
 * either creating conversations before a team exists (wrong - PROJECT_TEAM
 * is meaningless without a roster) or Chat independently re-deriving
 * "has Team been created for this project" itself, which is exactly the
 * kind of cross-module inference the boundary rule exists to prevent.
 *
 * Deliberately NOT wired: {@code ContributionMilestoneReachedEvent} and
 * {@code BadgeAwardedEvent}. Both are per-user, not per-conversation -
 * unlike every other consumed event here, there is no unambiguous target
 * conversation to post a system message into (a milestone isn't scoped to
 * one project). Routing these to the user directly is Notification's job,
 * not a Chat system message; forcing a "pick some conversation" heuristic
 * here would be a worse design than declining to guess. Listener methods
 * are present but only log at DEBUG, so this decision is visible in the
 * running system rather than silently absent - see the module's Future
 * Extension Points for the follow-up if a per-user "Milestones" DM channel
 * is ever scoped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatModuleEventListener {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageService messageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamCreated(TeamCreatedEvent event) {
        safely(() -> {
            Conversation teamChat = conversationService.getOrCreateProjectConversation(
                    event.projectId(), event.teamId(), ConversationType.PROJECT_TEAM, event.founderUserId());
            Conversation announcements = conversationService.getOrCreateProjectConversation(
                    event.projectId(), event.teamId(), ConversationType.PROJECT_ANNOUNCEMENTS, event.founderUserId());
            messageService.postSystemMessage(teamChat.getId(), "Conversation created.",
                    Map.of("eventType", "CONVERSATION_CREATED"));
            messageService.postSystemMessage(announcements.getId(), "Conversation created.",
                    Map.of("eventType", "CONVERSATION_CREATED"));
        }, "TeamCreatedEvent", event.teamId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoinedEvent event) {
        safely(() -> forEachProjectConversation(event.projectId(), conversation -> {
            conversationService.addMember(conversation.getId(), event.userId());
            messageService.postSystemMessage(conversation.getId(), "A new member joined the conversation.",
                    Map.of("eventType", "MEMBER_JOINED", "userId", event.userId().toString()));
        }), "MemberJoinedEvent", event.membershipId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberLeft(MemberLeftEvent event) {
        safely(() -> forEachProjectConversation(event.projectId(), conversation -> {
            removeIfPresent(conversation.getId(), event.userId(), null);
            messageService.postSystemMessage(conversation.getId(), "A member left the conversation.",
                    Map.of("eventType", "MEMBER_LEFT", "userId", event.userId().toString()));
        }), "MemberLeftEvent", event.membershipId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(MemberRemovedEvent event) {
        safely(() -> forEachProjectConversation(event.projectId(), conversation -> {
            removeIfPresent(conversation.getId(), event.userId(), event.removedBy());
            messageService.postSystemMessage(conversation.getId(), "A member was removed from the conversation.",
                    Map.of("eventType", "MEMBER_REMOVED", "userId", event.userId().toString()));
        }), "MemberRemovedEvent", event.membershipId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeadershipTransferred(LeadershipTransferredEvent event) {
        safely(() -> forEachProjectConversation(event.projectId(), conversation ->
                messageService.postSystemMessage(conversation.getId(), "Team lead changed.",
                        Map.of("eventType", "LEAD_CHANGED",
                                "previousLeadUserId", event.previousLeadUserId().toString(),
                                "newLeadUserId", event.newLeadUserId().toString()))
        ), "LeadershipTransferredEvent", event.teamId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCompleted(ProjectCompletedEvent event) {
        safely(() -> {
            forEachProjectConversation(event.projectId(), conversation ->
                    messageService.postSystemMessage(conversation.getId(), "This project has been completed.",
                            Map.of("eventType", "PROJECT_COMPLETED")));
            // Archive AFTER the system message is posted - archiveConversation
            // only rejects new *sends*, so the system message above (issued
            // from the event handler, not the read-only guard) still lands
            // in an ACTIVE conversation before it flips to ARCHIVED.
            conversationService.archiveAllForProject(event.projectId(), "Project completed");
        }, "ProjectCompletedEvent", event.projectId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectArchived(ProjectArchivedEvent event) {
        safely(() -> {
            forEachProjectConversation(event.projectId(), conversation ->
                    messageService.postSystemMessage(conversation.getId(), "This project was archived: " + event.reason(),
                            Map.of("eventType", "PROJECT_ARCHIVED", "reason", String.valueOf(event.reason()))));
            conversationService.lockAllForProject(event.projectId(), null, "Project archived: " + event.reason());
        }, "ProjectArchivedEvent", event.projectId());
    }

    /** Optional per module brief ("TaskAssigned (optional system message)"). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAssigned(TaskAssignedEvent event) {
        safely(() -> forEachProjectConversation(event.projectId(), conversation ->
                messageService.postSystemMessage(conversation.getId(), "A task was assigned.",
                        Map.of("eventType", "TASK_ASSIGNED", "taskId", event.taskId().toString(),
                                "assigneeId", event.assigneeId().toString()))
        ), "TaskAssignedEvent", event.taskId());
    }

    /** Optional per module brief ("TaskCompleted (optional celebration)"). Posted to
     * PROJECT_TEAM only, not Announcements - a completed task is team activity, not a broadcast. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedEvent event) {
        safely(() -> conversationRepository.findByProjectIdAndType(event.projectId(), ConversationType.PROJECT_TEAM)
                .ifPresent(conversation -> messageService.postSystemMessage(conversation.getId(), "\uD83C\uDF89 A task was completed!",
                        Map.of("eventType", "TASK_COMPLETED", "taskId", event.taskId().toString(),
                                "assigneeId", String.valueOf(event.assigneeId()))))
        , "TaskCompletedEvent", event.taskId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionMilestoneReached(ContributionMilestoneReachedEvent event) {
        log.debug("Chat intentionally does not auto-post ContributionMilestoneReachedEvent (user={}, milestone={}) - " +
                "no unambiguous target conversation; see ChatModuleEventListener's class javadoc.",
                event.userId(), event.milestoneType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBadgeAwarded(BadgeAwardedEvent event) {
        log.debug("Chat intentionally does not auto-post BadgeAwardedEvent (user={}, badge={}) - " +
                "no unambiguous target conversation; see ChatModuleEventListener's class javadoc.",
                event.userId(), event.badgeType());
    }

    // -------------------------------------------------------------------

    private void forEachProjectConversation(UUID projectId, java.util.function.Consumer<Conversation> action) {
        List<Conversation> conversations = conversationRepository.findByProjectId(projectId);
        for (Conversation conversation : conversations) {
            action.accept(conversation);
        }
    }

    private void removeIfPresent(UUID conversationId, UUID userId, UUID removedBy) {
        Optional<com.saanjha.modules.chat.entity.ConversationMember> member =
                memberRepository.findByConversationIdAndUserId(conversationId, userId);
        if (member.isEmpty() || !member.get().isLive()) {
            return; // idempotent - already removed by a prior/duplicate delivery
        }
        if (removedBy != null) {
            conversationService.removeMember(conversationId, userId, removedBy, "Removed from project team");
        } else {
            conversationService.leaveConversation(conversationId, userId);
        }
    }

    /** Same defensive-consumer pattern as TeamEventListener's own safely() -
     * a failure processing one event must never break the delivering
     * module's transaction (already committed) or a sibling handler's own
     * processing of the same event batch. */
    private void safely(Runnable action, String eventName, UUID correlationId) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Chat module failed to process {} (correlationId={}): {}", eventName, correlationId, ex.getMessage(), ex);
        }
    }
}
