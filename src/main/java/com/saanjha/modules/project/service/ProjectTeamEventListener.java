package com.saanjha.modules.project.service;

import com.saanjha.modules.team.event.TeamEvents.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Project's half of the leadership/team-size sync contract described in the
 * Team module's approved architecture spec (Sections 10–11). Team is
 * authoritative; this listener is the ONLY code path allowed to write
 * {@code Project.leadUserId}/{@code currentTeamSize} in response to a roster
 * change — Team never calls into Project synchronously for a write.
 *
 * Uses {@code @TransactionalEventListener} (AFTER_COMMIT) rather than a plain
 * {@code @EventListener}: Team's own transaction must fully commit before
 * Project's cache update is attempted, so a rollback in Team never leaves
 * Project's cache pointing at a roster change that didn't actually happen.
 */
@Component
@RequiredArgsConstructor
public class ProjectTeamEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectTeamEventListener.class);

    private final ProjectService projectService;

    @TransactionalEventListener
    public void onMemberJoined(MemberJoinedEvent event) {
        syncTeamSizeSafely(event.projectId(), event.currentTeamSize());
    }

    @TransactionalEventListener
    public void onMemberLeft(MemberLeftEvent event) {
        syncTeamSizeSafely(event.projectId(), event.currentTeamSize());
    }

    @TransactionalEventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        syncTeamSizeSafely(event.projectId(), event.currentTeamSize());
    }

    @TransactionalEventListener
    public void onLeadershipTransferred(LeadershipTransferredEvent event) {
        try {
            projectService.syncLeadership(event.projectId(), event.newLeadUserId());
        } catch (Exception ex) {
            // A failed cache sync must never surface as a failure of the transfer
            // that already committed in Team — log loudly and move on. Team's
            // membership row remains the source of truth regardless of whether
            // this cache write succeeded.
            log.error("Failed to sync leadUserId for project {} after transfer to {}", event.projectId(), event.newLeadUserId(), ex);
        }
    }

    private void syncTeamSizeSafely(java.util.UUID projectId, int currentTeamSize) {
        try {
            projectService.syncTeamSize(projectId, currentTeamSize);
        } catch (Exception ex) {
            log.error("Failed to sync currentTeamSize for project {}", projectId, ex);
        }
    }
}
