package com.saanjha.modules.notification.rule;

/**
 * Every domain event this module turns into a {@code Notification}. This is
 * deliberately NOT every event in the system's event catalog - some are
 * intentionally not notification-worthy (self-triggered actions the actor
 * already knows about, e.g. {@code ProfileUpdatedEvent}), some are too
 * high-frequency to be user-facing (e.g. {@code ContributionRecordedEvent},
 * {@code ReputationUpdatedEvent}), and some cannot be safely resolved to a
 * recipient without a cross-module read this module deliberately declines
 * to add (e.g. {@code ProjectArchivedEvent}, most {@code Task*Event}s beyond
 * assignment). Every omission is explained in {@code NotificationEventListener}'s
 * class javadoc and in the module's final report - "not implemented" and
 * "considered and declined" are different things, and this codebase's own
 * review culture (see event-catalog.md, architecture-review.md) treats that
 * distinction as important, so this enum preserves it rather than silently
 * dropping the unhandled events.
 */
public enum NotificationEventType {
    // auth
    USER_REGISTERED,
    SUSPICIOUS_ACTIVITY_DETECTED,
    // user
    PROFILE_COMPLETED,
    // project
    PROJECT_COMPLETED,
    // application
    APPLICATION_SUBMITTED,
    APPLICATION_WITHDRAWN,
    APPLICATION_SHORTLISTED,
    APPLICATION_ACCEPTED,
    APPLICATION_REJECTED,
    APPLICATION_EXPIRED,
    APPLICATION_REOPENED,
    // invitation
    INVITATION_SENT,
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    INVITATION_EXPIRED,
    INVITATION_REVOKED,
    INVITATION_SEAT_LOST_INVITEE,
    INVITATION_SEAT_LOST_LEAD,
    // team
    MEMBER_JOINED,
    MEMBER_REMOVED,
    LEADERSHIP_TRANSFERRED_TO,
    LEADERSHIP_TRANSFERRED_FROM,
    MEMBER_ROLE_CHANGED,
    TEAM_ARCHIVED,
    TEAM_DISSOLVED,
    MEMBERSHIP_CREATION_REJECTED,
    MEMBER_SUSPENDED,
    MEMBER_REINSTATED,
    // task
    TASK_ASSIGNED,
    TASK_UNASSIGNED,
    TASK_COMPLETED_FOR_REPORTER,
    // contribution
    CONTRIBUTION_MILESTONE_REACHED,
    CONTRIBUTION_CORRECTED,
    // portfolio
    PORTFOLIO_ENTRY_CREATED,
    BADGE_AWARDED
}
