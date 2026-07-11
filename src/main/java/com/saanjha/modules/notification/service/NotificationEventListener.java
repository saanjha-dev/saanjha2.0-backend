package com.saanjha.modules.notification.service;

import com.saanjha.modules.application.event.ApplicationEvents.*;
import com.saanjha.modules.application.event.InvitationEvents.*;
import com.saanjha.modules.auth.event.AuthEvents.SuspiciousActivityDetectedEvent;
import com.saanjha.modules.auth.event.AuthEvents.UserRegisteredEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionCorrectedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionMilestoneReachedEvent;
import com.saanjha.modules.notification.rule.NotificationEventType;
import com.saanjha.modules.notification.service.NotificationOrchestrationService.EnqueueCommand;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioEntryCreatedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.task.event.TaskEvents.TaskAssignedEvent;
import com.saanjha.modules.task.event.TaskEvents.TaskCompletedEvent;
import com.saanjha.modules.task.event.TaskEvents.TaskUnassignedEvent;
import com.saanjha.modules.team.event.TeamEvents.*;
import com.saanjha.modules.user.event.UserEvents.ProfileCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every consumed event in the platform lands here (module brief Step 2/3:
 * "Study every published event ... Build notification rules"). Thin adapter
 * layer only, same discipline as {@code TeamEventListener}/{@code
 * PortfolioEventListener}: translate payload -&gt; {@code EnqueueCommand},
 * delegate to {@link NotificationOrchestrationService}, nothing else. Every
 * method is wrapped in {@link #safely}, same reasoning as Team's listener -
 * a failure reacting to an event this module didn't publish must never
 * propagate back into an already-committed caller's transaction.
 * <p>
 * <b>Deliberately NOT consumed here</b> (see {@code NotificationEventType}'s
 * javadoc and the module's final report for the full reasoning per event):
 * {@code OtpGeneratedEvent} (auth's own delivery mechanism, already sends
 * its own email); {@code ProfileUpdatedEvent} (self-triggered, the actor
 * already knows); {@code ProjectStatusChangedEvent}/{@code ProjectArchivedEvent}
 * (no resolvable recipient in payload - superseded for the meaningful cases
 * by {@code TeamArchivedEvent}/{@code TeamDissolvedEvent}, which do carry a
 * roster); {@code ApplicationShortlistedEvent}/{@code AcceptedEvent}/{@code
 * RejectedEvent}/{@code ExpiredEvent}/{@code ReopenedEvent} - wait, these
 * ARE consumed, see below, this note is only for the truly-skipped ones;
 * {@code InvitationSentEvent}/{@code DeclinedEvent}/{@code RevokedEvent} -
 * also consumed, see below; {@code TeamCreatedEvent}/{@code MemberLeftEvent}
 * (self-triggered by the acting user); {@code TeamLockedEvent}/{@code
 * TeamUnlockedEvent} (no roster in payload today - a real, documented gap,
 * same shape as the one {@code TeamArchivedEvent} used to have before
 * ADR-005 closed it - recommended as this module's own new finding, see
 * final report); most {@code Task*Event}s beyond assignment/completion (no
 * resolvable recipient, or self-triggered); {@code ContributionRecordedEvent}/
 * {@code ReputationUpdatedEvent}/{@code ContributionSnapshotCreatedEvent}
 * (too high-frequency to be user-facing); {@code PortfolioGeneratedEvent}
 * (thinner duplicate of {@code PortfolioEntryCreatedEvent} for the same
 * instant); {@code PortfolioVisibilityChangedEvent}/{@code
 * PortfolioExportRequestedEvent} (self-triggered).
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationOrchestrationService orchestrationService;

    // ========================================================================
    // AUTH
    // ========================================================================

    @TransactionalEventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.USER_REGISTERED, event.userId().toString(),
                vars("title", "Welcome to Saanjha!", "email", event.email()))),
                "notify on user registration " + event.userId());
    }

    /**
     * See event-catalog.md: this event previously had NO consumer at all -
     * a detected refresh-token-replay attack was only visible via
     * {@code log.error}. This is the first real consumer it gets.
     */
    @TransactionalEventListener
    public void onSuspiciousActivityDetected(SuspiciousActivityDetectedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.SUSPICIOUS_ACTIVITY_DETECTED,
                event.userId() + ":" + event.timestamp(),
                vars("title", "Suspicious sign-in activity detected", "ipAddress", event.ipAddress(), "reason", event.reason()))),
                "notify on suspicious activity for " + event.userId());
    }

    // ========================================================================
    // USER
    // ========================================================================

    @TransactionalEventListener
    public void onProfileCompleted(ProfileCompletedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.PROFILE_COMPLETED, event.userId().toString(),
                vars("title", "Your profile is complete!"))),
                "notify on profile completion for " + event.userId());
    }

    // ========================================================================
    // PROJECT
    // ========================================================================

    @TransactionalEventListener
    public void onProjectCompleted(ProjectCompletedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.PROJECT_COMPLETED, event.projectId().toString(),
                vars("title", "Your project wrapped up", "projectId", event.projectId().toString(),
                        "actionUrl", "/projects/" + event.projectId()))),
                "notify lead on project completion " + event.projectId());
    }

    // ========================================================================
    // APPLICATION
    // ========================================================================

    @TransactionalEventListener
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.APPLICATION_SUBMITTED, event.applicationId().toString(),
                vars("title", "New application received", "actionUrl", "/applications/" + event.applicationId()))),
                "notify lead of application submission " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationWithdrawn(ApplicationWithdrawnEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.APPLICATION_WITHDRAWN, event.applicationId().toString(),
                vars("title", "An applicant withdrew their application"))),
                "notify lead of application withdrawal " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationShortlisted(ApplicationShortlistedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.applicantId(), NotificationEventType.APPLICATION_SHORTLISTED, event.applicationId().toString(),
                vars("title", "You've been shortlisted!", "actionUrl", "/applications/" + event.applicationId()))),
                "notify applicant of shortlisting " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationAccepted(ApplicationAcceptedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.applicantId(), NotificationEventType.APPLICATION_ACCEPTED, event.applicationId().toString(),
                vars("title", "Your application was accepted!", "actionUrl", "/projects/" + event.projectId()))),
                "notify applicant of acceptance " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationRejected(ApplicationRejectedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.applicantId(), NotificationEventType.APPLICATION_REJECTED, event.applicationId().toString(),
                vars("title", "Update on your application"))),
                "notify applicant of rejection " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationExpired(ApplicationExpiredEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.applicantId(), NotificationEventType.APPLICATION_EXPIRED, event.applicationId().toString(),
                vars("title", "Your application has expired"))),
                "notify applicant of expiry " + event.applicationId());
    }

    @TransactionalEventListener
    public void onApplicationReopened(ApplicationReopenedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.applicantId(), NotificationEventType.APPLICATION_REOPENED, event.applicationId() + ":" + event.occurredAt(),
                vars("title", "Your application is back under review"))),
                "notify applicant of reopening " + event.applicationId());
    }

    // ========================================================================
    // INVITATION
    // ========================================================================

    @TransactionalEventListener
    public void onInvitationSent(InvitationSentEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.invitedUserId(), NotificationEventType.INVITATION_SENT, event.invitationId().toString(),
                vars("title", "You've been invited to join a project", "actionUrl", "/invitations/" + event.invitationId()))),
                "notify invitee of invitation " + event.invitationId());
    }

    @TransactionalEventListener
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.INVITATION_ACCEPTED, event.invitationId().toString(),
                vars("title", "Your invitation was accepted"))),
                "notify lead of invitation acceptance " + event.invitationId());
    }

    @TransactionalEventListener
    public void onInvitationDeclined(InvitationDeclinedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.INVITATION_DECLINED, event.invitationId().toString(),
                vars("title", "Your invitation was declined"))),
                "notify lead of invitation decline " + event.invitationId());
    }

    @TransactionalEventListener
    public void onInvitationExpired(InvitationExpiredEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.invitedUserId(), NotificationEventType.INVITATION_EXPIRED, event.invitationId().toString(),
                vars("title", "An invitation you received has expired"))),
                "notify invitee of invitation expiry " + event.invitationId());
    }

    @TransactionalEventListener
    public void onInvitationRevoked(InvitationRevokedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.invitedUserId(), NotificationEventType.INVITATION_REVOKED, event.invitationId().toString(),
                vars("title", "An invitation was withdrawn"))),
                "notify invitee of invitation revocation " + event.invitationId());
    }

    @TransactionalEventListener
    public void onInvitationSeatLost(InvitationSeatLostEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.invitedUserId(), NotificationEventType.INVITATION_SEAT_LOST_INVITEE, event.invitationId() + ":invitee",
                vars("title", "Your seat on this project could no longer be held", "reason", event.reason()))),
                "notify invitee of seat loss " + event.invitationId());
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.leadUserId(), NotificationEventType.INVITATION_SEAT_LOST_LEAD, event.invitationId() + ":lead",
                vars("title", "A seat opened back up - consider inviting someone else", "reason", event.reason()))),
                "notify lead of seat loss " + event.invitationId());
    }

    // ========================================================================
    // TEAM
    // ========================================================================

    @TransactionalEventListener
    public void onMemberJoined(MemberJoinedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBER_JOINED, event.membershipId().toString(),
                vars("title", "Welcome to the team!", "actionUrl", "/projects/" + event.projectId()))),
                "notify new member " + event.membershipId());
    }

    @TransactionalEventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBER_REMOVED, event.membershipId().toString(),
                vars("title", "You were removed from a team", "reason", event.reason()))),
                "notify removed member " + event.membershipId());
    }

    @TransactionalEventListener
    public void onLeadershipTransferred(LeadershipTransferredEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.newLeadUserId(), NotificationEventType.LEADERSHIP_TRANSFERRED_TO, event.teamId() + ":" + event.occurredAt() + ":to",
                vars("title", "You are now the project lead", "actionUrl", "/projects/" + event.projectId()))),
                "notify new lead " + event.teamId());
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.previousLeadUserId(), NotificationEventType.LEADERSHIP_TRANSFERRED_FROM, event.teamId() + ":" + event.occurredAt() + ":from",
                vars("title", "You transferred project leadership"))),
                "notify previous lead " + event.teamId());
    }

    @TransactionalEventListener
    public void onMemberRoleChanged(MemberRoleChangedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBER_ROLE_CHANGED, event.teamId() + ":" + event.userId() + ":" + event.occurredAt(),
                vars("title", "Your role on the team changed", "fromRole", event.fromRole(), "toRole", event.toRole()))),
                "notify member of role change " + event.teamId());
    }

    /** Fans out to every member of the roster - this is the "event enrichment chaining" hook roadmap.md flagged for Portfolio, applied identically here. */
    @TransactionalEventListener
    public void onTeamArchived(TeamArchivedEvent event) {
        for (ArchivedMember member : event.roster()) {
            safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                    member.userId(), NotificationEventType.TEAM_ARCHIVED, event.teamId() + ":" + member.userId(),
                    vars("title", "A project you worked on has wrapped up", "actionUrl", "/portfolio"))),
                    "notify roster member " + member.userId() + " of team archival " + event.teamId());
        }
    }

    @TransactionalEventListener
    public void onTeamDissolved(TeamDissolvedEvent event) {
        for (ArchivedMember member : event.roster()) {
            safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                    member.userId(), NotificationEventType.TEAM_DISSOLVED, event.teamId() + ":" + member.userId(),
                    vars("title", "A team you were on was dissolved", "reason", event.reason()))),
                    "notify roster member " + member.userId() + " of team dissolution " + event.teamId());
        }
    }

    /**
     * Closes the long-standing dangling event flagged in event-catalog.md/
     * roadmap.md (S13/TD20): before this, a triggered capacity race left the
     * affected user's application/invitation stuck at ACCEPTED with no seat
     * and no signal reaching them at all.
     */
    @TransactionalEventListener
    public void onMembershipCreationRejected(MembershipCreationRejectedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBERSHIP_CREATION_REJECTED, event.sourceReferenceId().toString(),
                vars("title", "We couldn't confirm your seat on this team", "reason", event.reason()))),
                "notify user of membership creation rejection " + event.sourceReferenceId());
    }

    @TransactionalEventListener
    public void onMemberSuspended(MemberSuspendedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBER_SUSPENDED, event.membershipId().toString(),
                vars("title", "Your team membership was suspended", "reason", event.reason()))),
                "notify member of suspension " + event.membershipId());
    }

    @TransactionalEventListener
    public void onMemberReinstated(MemberReinstatedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.MEMBER_REINSTATED, event.membershipId() + ":" + event.occurredAt(),
                vars("title", "Your team membership was reinstated"))),
                "notify member of reinstatement " + event.membershipId());
    }

    // ========================================================================
    // TASK
    // ========================================================================

    @TransactionalEventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        if (event.assignedBy() != null && event.assignedBy().equals(event.assigneeId())) {
            return; // Self-assignment - the actor already knows.
        }
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.assigneeId(), NotificationEventType.TASK_ASSIGNED, event.taskId() + ":" + event.occurredAt(),
                vars("title", "You were assigned a new task", "actionUrl", "/tasks/" + event.taskId()))),
                "notify assignee of task assignment " + event.taskId());
    }

    @TransactionalEventListener
    public void onTaskUnassigned(TaskUnassignedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.previousAssigneeId(), NotificationEventType.TASK_UNASSIGNED, event.taskId() + ":" + event.occurredAt(),
                vars("title", "You were unassigned from a task", "reason", event.reason()))),
                "notify previous assignee of unassignment " + event.taskId());
    }

    @TransactionalEventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (event.reporterId() == null || event.reporterId().equals(event.assigneeId())) {
            return; // No distinct reporter to notify, or the assignee filed their own task.
        }
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.reporterId(), NotificationEventType.TASK_COMPLETED_FOR_REPORTER, event.taskId().toString(),
                vars("title", "A task you reported was completed", "actionUrl", "/tasks/" + event.taskId()))),
                "notify reporter of task completion " + event.taskId());
    }

    // ========================================================================
    // CONTRIBUTION
    // ========================================================================

    @TransactionalEventListener
    public void onContributionMilestoneReached(ContributionMilestoneReachedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.CONTRIBUTION_MILESTONE_REACHED, event.userId() + ":" + event.milestoneType() + ":" + event.milestoneValue(),
                vars("title", "Milestone reached: " + event.milestoneValue() + " " + event.milestoneType(),
                        "actionUrl", "/portfolio"))),
                "notify user of contribution milestone " + event.userId());
    }

    /** Priority LOW, IN_APP-only per this event's own javadoc ("rare, usually silent"). */
    @TransactionalEventListener
    public void onContributionCorrected(ContributionCorrectedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.CONTRIBUTION_CORRECTED, event.reversalEntryId().toString(),
                vars("title", "Your contribution score was adjusted", "reason", event.reason()))),
                "notify user of contribution correction " + event.reversalEntryId());
    }

    // ========================================================================
    // PORTFOLIO
    // ========================================================================

    @TransactionalEventListener
    public void onPortfolioEntryCreated(PortfolioEntryCreatedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.PORTFOLIO_ENTRY_CREATED, event.entryId().toString(),
                vars("title", "Your portfolio grew", "actionUrl", "/portfolio"))),
                "notify user of portfolio entry " + event.entryId());
    }

    @TransactionalEventListener
    public void onBadgeAwarded(BadgeAwardedEvent event) {
        safely(() -> orchestrationService.enqueue(new EnqueueCommand(
                event.userId(), NotificationEventType.BADGE_AWARDED, event.userId() + ":" + event.badgeType(),
                vars("title", "Achievement unlocked: " + event.badgeType(), "actionUrl", "/portfolio"))),
                "notify user of badge award " + event.userId());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    /** Same reasoning as {@code TeamEventListener.safely}: a failure reacting to an already-committed upstream event must never propagate. */
    private void safely(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Notification module failed to {}", description, ex);
        }
    }
}
