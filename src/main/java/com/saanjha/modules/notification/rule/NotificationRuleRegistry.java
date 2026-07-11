package com.saanjha.modules.notification.rule;

import com.saanjha.modules.notification.entity.NotificationCategory;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationPriority;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.saanjha.modules.notification.entity.NotificationChannel.*;
import static com.saanjha.modules.notification.entity.NotificationCategory.*;
import static com.saanjha.modules.notification.entity.NotificationPriority.*;
import static com.saanjha.modules.notification.rule.NotificationEventType.*;

/**
 * Every {@link NotificationEventType} MUST have an entry here - completeness
 * is asserted by {@code NotificationRuleRegistryTest}, which iterates
 * {@code NotificationEventType.values()} rather than listing them by hand, so
 * adding a new enum constant without adding its rule fails the build instead
 * of silently falling through to a runtime default.
 */
public final class NotificationRuleRegistry {

    private static final Map<NotificationEventType, NotificationRule> RULES = new EnumMap<>(NotificationEventType.class);

    static {
        // auth
        put(USER_REGISTERED, ACCOUNT, LOW, Set.of(IN_APP, EMAIL));
        put(SUSPICIOUS_ACTIVITY_DETECTED, SECURITY, CRITICAL, Set.of(EMAIL, IN_APP));
        // user
        put(PROFILE_COMPLETED, ACCOUNT, LOW, Set.of(IN_APP, EMAIL));
        // project
        put(PROJECT_COMPLETED, PROJECT, NORMAL, Set.of(IN_APP, EMAIL));
        // application
        put(APPLICATION_SUBMITTED, APPLICATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(APPLICATION_WITHDRAWN, APPLICATION, LOW, Set.of(IN_APP));
        put(APPLICATION_SHORTLISTED, APPLICATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(APPLICATION_ACCEPTED, APPLICATION, HIGH, Set.of(IN_APP, EMAIL, PUSH));
        put(APPLICATION_REJECTED, APPLICATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(APPLICATION_EXPIRED, APPLICATION, LOW, Set.of(IN_APP));
        put(APPLICATION_REOPENED, APPLICATION, NORMAL, Set.of(IN_APP, EMAIL));
        // invitation
        put(INVITATION_SENT, INVITATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(INVITATION_ACCEPTED, INVITATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(INVITATION_DECLINED, INVITATION, LOW, Set.of(IN_APP));
        put(INVITATION_EXPIRED, INVITATION, LOW, Set.of(IN_APP));
        put(INVITATION_REVOKED, INVITATION, NORMAL, Set.of(IN_APP, EMAIL));
        put(INVITATION_SEAT_LOST_INVITEE, INVITATION, HIGH, Set.of(IN_APP, EMAIL));
        put(INVITATION_SEAT_LOST_LEAD, INVITATION, NORMAL, Set.of(IN_APP, EMAIL));
        // team
        put(MEMBER_JOINED, TEAM, NORMAL, Set.of(IN_APP, EMAIL));
        put(MEMBER_REMOVED, TEAM, HIGH, Set.of(IN_APP, EMAIL));
        put(LEADERSHIP_TRANSFERRED_TO, TEAM, HIGH, Set.of(IN_APP, EMAIL));
        put(LEADERSHIP_TRANSFERRED_FROM, TEAM, NORMAL, Set.of(IN_APP));
        put(MEMBER_ROLE_CHANGED, TEAM, LOW, Set.of(IN_APP));
        put(TEAM_ARCHIVED, PROJECT, NORMAL, Set.of(IN_APP, EMAIL));
        put(TEAM_DISSOLVED, TEAM, HIGH, Set.of(IN_APP, EMAIL));
        put(MEMBERSHIP_CREATION_REJECTED, TEAM, HIGH, Set.of(IN_APP, EMAIL));
        put(MEMBER_SUSPENDED, TEAM, HIGH, Set.of(IN_APP, EMAIL));
        put(MEMBER_REINSTATED, TEAM, NORMAL, Set.of(IN_APP, EMAIL));
        // task
        put(TASK_ASSIGNED, TASK, NORMAL, Set.of(IN_APP, PUSH));
        put(TASK_UNASSIGNED, TASK, LOW, Set.of(IN_APP));
        put(TASK_COMPLETED_FOR_REPORTER, TASK, NORMAL, Set.of(IN_APP));
        // contribution
        put(CONTRIBUTION_MILESTONE_REACHED, CONTRIBUTION, NORMAL, Set.of(IN_APP, EMAIL));
        put(CONTRIBUTION_CORRECTED, CONTRIBUTION, LOW, Set.of(IN_APP));
        // portfolio
        put(PORTFOLIO_ENTRY_CREATED, PORTFOLIO, LOW, Set.of(IN_APP));
        put(BADGE_AWARDED, PORTFOLIO, NORMAL, Set.of(IN_APP, EMAIL));
    }

    private NotificationRuleRegistry() {
    }

    public static NotificationRule get(NotificationEventType type) {
        NotificationRule rule = RULES.get(type);
        if (rule == null) {
            // Defensive only - NotificationRuleRegistryTest asserts this can never
            // happen for a real enum constant. Falls back to a safe, quiet default
            // rather than throwing, so a future rule-registration miss degrades to
            // "under-notifies" instead of breaking the publishing transaction's caller.
            return new NotificationRule(NotificationCategory.ACCOUNT, NotificationPriority.LOW, Set.of(IN_APP));
        }
        return rule;
    }

    private static void put(NotificationEventType type, NotificationCategory category, NotificationPriority priority,
                             Set<NotificationChannel> channels) {
        RULES.put(type, new NotificationRule(category, priority, channels));
    }
}
