package com.saanjha.modules.team.service;

import com.saanjha.modules.application.event.ApplicationEvents.ApplicationAcceptedEvent;
import com.saanjha.modules.application.event.InvitationEvents.InvitationAcceptedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectStatusChangedEvent;
import com.saanjha.modules.team.entity.MembershipSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin adapter layer: every method here just translates an external event
 * into a {@code TeamService} call. All actual business logic (idempotency,
 * capacity checks, lifecycle rules) lives in the service, not here — this
 * class's only job is "which event maps to which internal action."
 *
 * {@code @TransactionalEventListener} (default phase AFTER_COMMIT) is used
 * throughout: Team must never react to a Project/Application/Invitation
 * change that ends up rolling back.
 */
@Component
@RequiredArgsConstructor
public class TeamEventListener {

    private static final Logger log = LoggerFactory.getLogger(TeamEventListener.class);

    private final TeamService teamService;

    @TransactionalEventListener
    public void onProjectPublished(com.saanjha.modules.project.event.ProjectEvents.ProjectPublishedEvent event) {
        safely(() -> teamService.getOrCreateTeam(event.projectId(), event.leadUserId()),
                "seed team for project " + event.projectId());
    }

    @TransactionalEventListener
    public void onProjectStatusChanged(ProjectStatusChangedEvent event) {
        if ("IN_PROGRESS".equals(event.toStatus())) {
            safely(() -> teamService.activateTeam(event.projectId()), "activate team for project " + event.projectId());
        }
    }

    @TransactionalEventListener
    public void onProjectCompleted(ProjectCompletedEvent event) {
        safely(() -> teamService.archiveWithTeam(event.projectId()), "archive team for completed project " + event.projectId());
    }

    @TransactionalEventListener
    public void onProjectArchived(ProjectArchivedEvent event) {
        safely(() -> teamService.archiveWithTeam(event.projectId()), "archive team for archived project " + event.projectId());
    }

    @TransactionalEventListener
    public void onApplicationAccepted(ApplicationAcceptedEvent event) {
        safely(() -> teamService.addMember(event.projectId(), event.applicantId(), MembershipSource.APPLICATION, event.applicationId()),
                "add member from accepted application " + event.applicationId());
    }

    @TransactionalEventListener
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        safely(() -> teamService.addMember(event.projectId(), event.invitedUserId(), MembershipSource.INVITATION, event.invitationId()),
                "add member from accepted invitation " + event.invitationId());
    }

    /**
     * A failure reacting to an upstream event must never propagate back and
     * fail the transaction that already committed in Project/Application —
     * that transaction is done. Log loudly instead; these are the seams
     * worth alerting on once real observability exists.
     */
    private void safely(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Team module failed to {}", description, ex);
        }
    }
}
