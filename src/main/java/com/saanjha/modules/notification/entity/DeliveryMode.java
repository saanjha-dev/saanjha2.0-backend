package com.saanjha.modules.notification.entity;

/** INSTANT dispatches as soon as preferences/quiet-hours allow it. DIGEST batches by delaying
 *  {@code scheduledFor} to the user's next digest window; the dispatcher is unaware of the
 *  distinction; it just doesn't pick up rows before {@code scheduledFor} (see NotificationDispatchService). */
public enum DeliveryMode {
    INSTANT,
    DIGEST
}
