package com.saanjha.modules.task.service;

import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.team.event.TeamEvents.MemberRemovedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Task's event consumption, deliberately narrower than the module brief's
 * literal consume-list (TeamCreatedEvent, MemberJoinedEvent,
 * MemberRemovedEvent, ProjectArchivedEvent, ProjectCompletedEvent,
 * LeadershipTransferredEvent). Challenging that list rather than wiring all
 * six blindly:
 *
 * <ul>
 *   <li><b>{@code MemberRemovedEvent} — WIRED.</b> "Assignee removed from
 *       team" is an explicit business rule; tasks assigned to a departed
 *       member must be unassigned.</li>
 *   <li><b>{@code ProjectCompletedEvent} / {@code ProjectArchivedEvent} —
 *       WIRED.</b> "Completed projects cannot modify tasks" plus natural
 *       cleanup: every task is force-archived when its project ends.</li>
 *   <li><b>{@code TeamCreatedEvent} — NOT wired.</b> There is no Task-side
 *       data to seed when a team forms; Task has no per-project "board"
 *       wrapper entity to create (see {@code Task}'s own javadoc on why no
 *       such wrapper exists). A project simply has zero tasks until someone
 *       creates one — nothing to initialize.</li>
 *   <li><b>{@code MemberJoinedEvent} — NOT wired.</b> Task never caches team
 *       membership; every assignment validity check
 *       ({@code assertValidAssignee}) queries Team live via
 *       {@code TeamSecurityGuard.isMemberOfProjectsTeam}. Caching a
 *       membership list here would just be a second, harder-to-invalidate
 *       copy of data Team already owns and already answers quickly.</li>
 *   <li><b>{@code LeadershipTransferredEvent} — NOT wired.</b> Nothing in
 *       Task's authorization model depends on who the Lead is at a cached
 *       point in time — any Lead-gated action (should one exist) would
 *       check Project's already-synced {@code leadUserId} cache live, the
 *       same way Project/Application already do (see ADR-004). A transfer
 *       is correctly reflected on Task's very next request with zero
 *       Task-side event handling required.</li>
 * </ul>
 *
 * This is not an oversight — wiring a listener with nothing meaningful to do
 * is worse than not wiring it: it's a maintenance burden and a false signal
 * to the next engineer that "this module reacts to leadership changes,"
 * which it doesn't need to.
 */
@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEventListener.class);

    private final TaskService taskService;

    @TransactionalEventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        safely(() -> taskService.handleMemberRemoved(event.projectId(), event.userId()),
                "unassign tasks for removed member " + event.userId() + " on project " + event.projectId());
    }

    @TransactionalEventListener
    public void onProjectCompleted(ProjectCompletedEvent event) {
        safely(() -> taskService.archiveAllForProject(event.projectId()),
                "archive all tasks for completed project " + event.projectId());
    }

    @TransactionalEventListener
    public void onProjectArchived(ProjectArchivedEvent event) {
        safely(() -> taskService.archiveAllForProject(event.projectId()),
                "archive all tasks for archived project " + event.projectId());
    }

    private void safely(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Task module failed to {}", description, ex);
        }
    }
}
