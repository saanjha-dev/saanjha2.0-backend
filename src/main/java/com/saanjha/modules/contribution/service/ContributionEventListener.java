package com.saanjha.modules.contribution.service;

import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.task.event.TaskEvents.*;
import com.saanjha.modules.team.event.TeamEvents.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Contribution's ONLY window into the rest of the platform. Every method
 * here is a thin adapter — no scoring logic lives here, only "which event
 * maps to which internal action," per the same discipline every prior
 * listener in this codebase follows.
 *
 * Consumes 7 of the brief's ~9 listed events. {@code TaskAssignedEvent} is
 * wired for integrity-tracking only (never a scored ledger entry — an
 * assignment isn't a contribution). Not wired, with reasoning:
 * {@code TaskReviewedEvent} does not exist anywhere in this codebase — Task
 * never published one; "a review happened" is currently only derivable from
 * {@code TaskCompletedEvent.reviewedBy()} being non-null. This is a real,
 * documented gap (see the module write-up's Extension Points): Task should
 * eventually distinguish "moved to DONE" from "review was positive" as
 * separate concepts, since today a Lead rejecting review work and simply
 * reassigning it looks identical, event-wise, to an approved review.
 */
@Component
@RequiredArgsConstructor
public class ContributionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ContributionEventListener.class);

    private final ContributionService contributionService;

    @TransactionalEventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        safely(() -> contributionService.trackAssignment(event.taskId()), "track assignment for task " + event.taskId());
    }

    @TransactionalEventListener
    public void onTaskReopened(TaskReopenedEvent event) {
        safely(() -> contributionService.trackReopen(event.taskId()), "track reopen for task " + event.taskId());
    }

    @TransactionalEventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        safely(() -> contributionService.recordTaskCompletion(
                        event.taskId(), event.projectId(), event.assigneeId(), event.reporterId(),
                        event.complexity(), event.priority(), event.estimatedHours(), event.actualHours(),
                        event.reviewedBy(), null, event.completedAt()),
                "score task completion for task " + event.taskId());
    }

    @TransactionalEventListener
    public void onTaskCancelled(TaskCancelledEvent event) {
        // Only meaningful if the task had an assignee actively working it — the
        // event itself doesn't carry assigneeId, so this is intentionally a
        // best-effort no-op today. Documented gap: TaskCancelledEvent should be
        // enriched with the assignee at time of cancellation for this to be
        // fully wired — see the module write-up's Extension Points.
        log.debug("TaskCancelledEvent received for task {} — abandonment tracking needs TaskCancelledEvent enriched with assigneeId (documented gap)", event.taskId());
    }

    @TransactionalEventListener
    public void onProjectCompleted(ProjectCompletedEvent event) {
        safely(() -> contributionService.recordProjectLeadershipSuccess(event.projectId(), event.leadUserId(), event.completedAt()),
                "record leadership success for project " + event.projectId());
    }

    @TransactionalEventListener
    public void onLeadershipTransferred(LeadershipTransferredEvent event) {
        // No dedicated transferId exists on this event. A team's teamId alone
        // would only let the FIRST-EVER transfer for that team ever score
        // (the uniqueness index would block every subsequent one). Deriving a
        // deterministic id from (teamId, occurredAt) instead means: a genuine
        // redelivery of the SAME event (same occurredAt) maps to the same
        // derived id and is correctly deduped, while a real, later transfer
        // (a different occurredAt) gets its own id and is correctly scored.
        UUID transferId = java.util.UUID.nameUUIDFromBytes((event.teamId().toString() + event.occurredAt()).getBytes());
        safely(() -> contributionService.recordLeadershipTransfer(event.projectId(), event.newLeadUserId(), transferId, event.occurredAt()),
                "record leadership transfer for project " + event.projectId());
    }

    @TransactionalEventListener
    public void onMemberJoined(MemberJoinedEvent event) {
        safely(() -> contributionService.trackTeamSizeChange(event.projectId(), event.currentTeamSize()),
                "track team size for project " + event.projectId());
    }

    @TransactionalEventListener
    public void onMemberLeft(MemberLeftEvent event) {
        safely(() -> contributionService.trackTeamSizeChange(event.projectId(), event.currentTeamSize()),
                "track team size for project " + event.projectId());
    }

    @TransactionalEventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        safely(() -> contributionService.trackTeamSizeChange(event.projectId(), event.currentTeamSize()),
                "track team size for project " + event.projectId());
    }

    private void safely(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Contribution module failed to {}", description, ex);
        }
    }
}
