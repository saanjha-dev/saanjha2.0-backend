package com.saanjha.modules.notification.entity;

/**
 * Per-channel delivery lifecycle. One {@link NotificationDelivery} row per
 * (notification, channel) carries exactly one of these at a time; every
 * transition is written to {@link ProviderAttempt} for audit (module brief:
 * "Each transition audited").
 *
 * <pre>
 * CREATED -> QUEUED -> PROCESSING -> SENT -> DELIVERED -> READ
 *                          |            \
 *                          v             v
 *                       FAILED <-----> RETRYING -> EXPIRED
 *                          |
 *                          v
 *                      CANCELLED (from CREATED/QUEUED only, e.g. source event superseded)
 * </pre>
 */
public enum DeliveryStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    RETRYING,
    EXPIRED,
    CANCELLED
}
