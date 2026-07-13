package com.saanjha.modules.admin.service;

import com.saanjha.modules.auth.event.AuthEvents.SuspiciousActivityDetectedEvent;
import com.saanjha.modules.chat.event.ChatEvents.ConversationLockedEvent;
import com.saanjha.modules.chat.event.ChatEvents.MessageDeletedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectPublishedEvent;
import com.saanjha.modules.team.event.TeamEvents.TeamDissolvedEvent;
import com.saanjha.modules.team.event.TeamEvents.MembershipCreationRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin adapter layer translating other modules' domain events into Admin's
 * own read-model/trust-signal updates — the same "adapter, no business logic
 * of its own" shape as {@code TeamEventListener}. {@code @TransactionalEventListener}
 * (default AFTER_COMMIT) throughout, for the same reason Team's listener uses
 * it: Admin must never react to a change elsewhere that ends up rolling back.
 *
 * {@link #onSuspiciousActivity} is the single most overdue wiring in this
 * codebase per event-catalog.md: {@code SuspiciousActivityDetectedEvent} has
 * existed since the Auth module shipped with zero consumers ("a detected
 * token-replay attack is only visible via log.error"). This is its first
 * real consumer.
 */
@Component
@RequiredArgsConstructor
public class AdminEventListener {

    private static final Logger log = LoggerFactory.getLogger(AdminEventListener.class);

    private final TrustScoreService trustScoreService;

    @TransactionalEventListener
    public void onSuspiciousActivity(SuspiciousActivityDetectedEvent event) {
        safely(() -> trustScoreService.recordSuspiciousActivity(event.userId()),
                "record suspicious activity for user " + event.userId());
    }

    @TransactionalEventListener
    public void onProjectPublished(ProjectPublishedEvent event) {
        // Read-model hook only today (dashboard trend data derives from a live
        // count via ProjectService in a future iteration); logged so the seam
        // is visible and testable without requiring a dedicated counter table
        // for a metric no dashboard view currently renders.
        log.debug("Admin observed ProjectPublishedEvent for project {}", event.projectId());
    }

    @TransactionalEventListener
    public void onProjectArchived(ProjectArchivedEvent event) {
        log.debug("Admin observed ProjectArchivedEvent for project {} (reason: {})", event.projectId(), event.reason());
    }

    @TransactionalEventListener
    public void onTeamDissolved(TeamDissolvedEvent event) {
        log.info("Admin observed TeamDissolvedEvent for team {} dissolved by {}", event.teamId(), event.dissolvedBy());
    }

    /**
     * Dangling compensating event (see event-catalog.md, "team (new)" table):
     * Application/Invitation already consume this to reopen the affected
     * record. Admin's own consumption here is purely observational — it
     * surfaces the race as a Trust & Safety signal (a capacity race is not a
     * user's fault, so this deliberately does NOT degrade a TrustScore) and
     * as an audit trail entry, so the operational fact "this race occurred"
     * is visible on Admin's timeline even though the compensating fix
     * already lives in the Application module.
     */
    @TransactionalEventListener
    public void onMembershipCreationRejected(MembershipCreationRejectedEvent event) {
        log.warn("Admin observed a MembershipCreationRejectedEvent (capacity race) for project {}, user {}", event.projectId(), event.userId());
    }

    @TransactionalEventListener
    public void onChatMessageDeleted(MessageDeletedEvent event) {
        log.debug("Admin observed a chat message removal (conversation {}), mirrored via Chat's own moderation_actions table.", event.conversationId());
    }

    @TransactionalEventListener
    public void onChatConversationLocked(ConversationLockedEvent event) {
        log.info("Admin observed a chat conversation lock (conversation {}): {}", event.conversationId(), event.reason());
    }

    private void safely(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Admin module failed to {}", description, ex);
        }
    }
}
