package com.saanjha.modules.notification.entity;

/**
 * Delivery channels this module actually dispatches through today. The
 * module brief also lists Slack/Discord/WhatsApp/Microsoft Teams/Browser
 * Push as "future" channels - adding one of those is meant to cost exactly
 * one new enum value + one new {@link com.saanjha.modules.notification.provider.NotificationProvider}
 * implementation registered in {@link com.saanjha.modules.notification.provider.ProviderChainResolver},
 * nothing else in the orchestration/retry/preference/template layers should
 * need to change. That extensibility claim is the actual design goal here,
 * not just a comment - see the module's Future Extension Points in the
 * final report.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
    IN_APP,
    WEBHOOK
}
