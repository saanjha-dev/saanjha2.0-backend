package com.saanjha.modules.notification.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuleRegistryTest {

    /**
     * The whole point of this test: it iterates the enum rather than listing
     * constants by hand, so adding a new {@link NotificationEventType} without
     * registering its rule fails the build immediately instead of silently
     * falling through to {@code NotificationRuleRegistry.get}'s defensive
     * default at runtime.
     */
    @Test
    void everyEventTypeHasARegisteredRule() {
        for (NotificationEventType type : NotificationEventType.values()) {
            NotificationRule rule = NotificationRuleRegistry.get(type);
            assertThat(rule).as("rule for %s", type).isNotNull();
            assertThat(rule.defaultChannels()).as("channels for %s", type).isNotEmpty();
        }
    }

    @Test
    void everyRuleIncludesInAppSoTheFeedNeverSilentlyMissesAnEvent() {
        // IN_APP is this platform's own read model, not an external channel that
        // can be legitimately unavailable - see NotificationPreferenceService's
        // reasoning for why it's never fully suppressible by a channel toggle either.
        for (NotificationEventType type : NotificationEventType.values()) {
            NotificationRule rule = NotificationRuleRegistry.get(type);
            assertThat(rule.defaultChannels())
                    .as("IN_APP presence for %s", type)
                    .contains(com.saanjha.modules.notification.entity.NotificationChannel.IN_APP);
        }
    }

    @Test
    void criticalPriorityGetsTheLargestRetryBudget() {
        NotificationRule critical = new NotificationRule(
                com.saanjha.modules.notification.entity.NotificationCategory.SECURITY,
                com.saanjha.modules.notification.entity.NotificationPriority.CRITICAL,
                java.util.Set.of(com.saanjha.modules.notification.entity.NotificationChannel.IN_APP));
        NotificationRule low = new NotificationRule(
                com.saanjha.modules.notification.entity.NotificationCategory.ACCOUNT,
                com.saanjha.modules.notification.entity.NotificationPriority.LOW,
                java.util.Set.of(com.saanjha.modules.notification.entity.NotificationChannel.IN_APP));

        assertThat(critical.maxAttemptsFor()).isGreaterThan(low.maxAttemptsFor());
        assertThat(critical.ttlFor()).isGreaterThan(low.ttlFor());
    }
}
