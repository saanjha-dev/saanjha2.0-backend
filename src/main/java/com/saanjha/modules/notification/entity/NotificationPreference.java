package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Global, per-user notification settings - the top tier of the two-tier
 * preference model (see {@link NotificationEventPreference} for the
 * per-event-type override tier). Row is created lazily with sane defaults
 * on first resolution (see {@code NotificationPreferenceService.getOrDefault}),
 * not seeded per-user at signup - there is no {@code UserRegisteredEvent}
 * dependency here, deliberately: Notification should never need to react to
 * every single new signup just to insert a defaults row.
 */
@Entity
@Table(name = "ntf_preferences", schema = "ntf")
@Getter
@Setter
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = false;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "webhook_enabled", nullable = false)
    private boolean webhookEnabled = false;

    /** Where WEBHOOK-channel deliveries are sent, if the user/integration has configured one. Without this set, WEBHOOK is enabled-but-inert for this user - see RecipientAddressResolver. */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "do_not_disturb", nullable = false)
    private boolean doNotDisturb = false;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "UTC";

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "default_mode", nullable = false, length = 10)
    private DeliveryMode defaultMode = DeliveryMode.INSTANT;

    /** Ordered JSON array of channel names, e.g. ["IN_APP","EMAIL","PUSH"] - the user's own priority when more than one channel would otherwise fire for the same event. Empty = no explicit preference, use the rule's default order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_priority", nullable = false, columnDefinition = "jsonb")
    private String channelPriorityJson = "[]";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static NotificationPreference defaults(UUID userId) {
        NotificationPreference p = new NotificationPreference();
        p.userId = userId;
        return p;
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case SMS -> smsEnabled;
            case PUSH -> pushEnabled;
            case IN_APP -> inAppEnabled;
            case WEBHOOK -> webhookEnabled;
        };
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
