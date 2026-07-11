package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-(user, eventType) override of the global {@link NotificationPreference}.
 * Absence of a row means "use the global default for this event type" -
 * these rows only exist for events the user has explicitly customized
 * (muted, or moved to digest, or restricted to fewer channels than the
 * global default would allow).
 * <p>
 * {@code enabled = false} mutes this specific event type entirely,
 * regardless of the global per-channel toggles - this is what
 * "Per-event preferences" in the module brief actually means in practice
 * (a user who wants Email in general but not for every single
 * TASK_ASSIGNED).
 */
@Entity
@Table(name = "ntf_event_preferences", schema = "ntf", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ntf_event_pref_user_event", columnNames = {"user_id", "event_type"})
})
@Getter
@Setter
public class NotificationEventPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 10)
    private DeliveryMode mode;

    public static NotificationEventPreference of(UUID userId, String eventType, boolean enabled, DeliveryMode mode) {
        NotificationEventPreference p = new NotificationEventPreference();
        p.userId = userId;
        p.eventType = eventType;
        p.enabled = enabled;
        p.mode = mode;
        return p;
    }
}
