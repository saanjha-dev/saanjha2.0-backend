package com.saanjha.modules.notification.service;

import com.saanjha.modules.notification.dto.NotificationRequestDTOs.UpdatePreferencesRequest;
import com.saanjha.modules.notification.dto.NotificationResponseDTOs.PreferencesResponse;
import com.saanjha.modules.notification.entity.*;
import com.saanjha.modules.notification.repository.NotificationEventPreferenceRepository;
import com.saanjha.modules.notification.repository.NotificationPreferenceRepository;
import com.saanjha.modules.notification.rule.NotificationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationEventPreferenceRepository eventPreferenceRepository;

    // FIX: Define a reusable constant for suppressed deliveries outside the record.
    private static final ResolvedDelivery SUPPRESSED_DELIVERY =
            new ResolvedDelivery(true, Set.of(), DeliveryMode.INSTANT, "en");

    // FIX: Removed the static method from the record to resolve naming collisions and modifier errors.
    public record ResolvedDelivery(boolean suppressed, Set<NotificationChannel> channels, DeliveryMode mode, String locale) {}

    @Transactional(readOnly = true)
    public ResolvedDelivery resolveDelivery(UUID userId, String eventType, NotificationRule rule) {
        NotificationPreference prefs = preferenceRepository.findByUserId(userId).orElseGet(() -> NotificationPreference.defaults(userId));
        Optional<NotificationEventPreference> override = eventPreferenceRepository.findByUserIdAndEventType(userId, eventType);

        if (override.isPresent() && !override.get().isEnabled()) {
            return SUPPRESSED_DELIVERY; // FIX: Use the constant
        }

        boolean bypassGating = rule.priority() == NotificationPriority.CRITICAL;

        if (!bypassGating && prefs.isDoNotDisturb()) {
            return SUPPRESSED_DELIVERY; // FIX: Use the constant
        }

        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        for (NotificationChannel channel : rule.defaultChannels()) {
            if (prefs.isChannelEnabled(channel)) {
                channels.add(channel);
            }
        }

        if (rule.defaultChannels().contains(NotificationChannel.IN_APP)) {
            channels.add(NotificationChannel.IN_APP);
        }

        if (channels.isEmpty()) {
            return SUPPRESSED_DELIVERY; // FIX: Use the constant
        }

        DeliveryMode mode = override.map(NotificationEventPreference::getMode).orElse(prefs.getDefaultMode());
        if (bypassGating) {
            mode = DeliveryMode.INSTANT;
        }

        boolean inQuietHours = !bypassGating && isWithinQuietHours(prefs);
        if (inQuietHours) {
            mode = DeliveryMode.DIGEST;
        }

        return new ResolvedDelivery(false, channels, mode, prefs.getLocale());
    }

    // ... (rest of the class remains identical) ...

    private boolean isWithinQuietHours(NotificationPreference prefs) {
        if (prefs.getQuietHoursStart() == null || prefs.getQuietHoursEnd() == null) {
            return false;
        }
        try {
            LocalTime now = java.time.ZonedDateTime.now(ZoneId.of(prefs.getTimezone())).toLocalTime();
            LocalTime start = prefs.getQuietHoursStart();
            LocalTime end = prefs.getQuietHoursEnd();
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            // Wraps past midnight, e.g. 22:00 -> 07:00.
            return !now.isBefore(start) || now.isBefore(end);
        } catch (Exception ex) {
            return false; // Unknown/invalid timezone string - never let a bad preference row silently suppress delivery.
        }
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(UUID userId) {
        NotificationPreference prefs = preferenceRepository.findByUserId(userId).orElseGet(() -> NotificationPreference.defaults(userId));
        return toResponse(prefs);
    }

    @Transactional
    public PreferencesResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        NotificationPreference prefs = preferenceRepository.findByUserId(userId).orElseGet(() -> NotificationPreference.defaults(userId));

        if (request.emailEnabled() != null) prefs.setEmailEnabled(request.emailEnabled());
        if (request.smsEnabled() != null) prefs.setSmsEnabled(request.smsEnabled());
        if (request.pushEnabled() != null) prefs.setPushEnabled(request.pushEnabled());
        if (request.inAppEnabled() != null) prefs.setInAppEnabled(request.inAppEnabled());
        if (request.webhookEnabled() != null) prefs.setWebhookEnabled(request.webhookEnabled());
        if (request.doNotDisturb() != null) prefs.setDoNotDisturb(request.doNotDisturb());
        if (request.quietHoursStart() != null) prefs.setQuietHoursStart(request.quietHoursStart());
        if (request.quietHoursEnd() != null) prefs.setQuietHoursEnd(request.quietHoursEnd());
        if (request.timezone() != null) prefs.setTimezone(request.timezone());
        if (request.locale() != null) prefs.setLocale(request.locale());
        if (request.defaultMode() != null) prefs.setDefaultMode(request.defaultMode());
        prefs.touch();

        preferenceRepository.save(prefs);
        return toResponse(prefs);
    }

    @Transactional
    public void setEventPreference(UUID userId, String eventType, boolean enabled, DeliveryMode mode) {
        NotificationEventPreference pref = eventPreferenceRepository.findByUserIdAndEventType(userId, eventType)
                .orElseGet(() -> NotificationEventPreference.of(userId, eventType, enabled, mode));
        pref.setEnabled(enabled);
        pref.setMode(mode);
        eventPreferenceRepository.save(pref);
    }

    private PreferencesResponse toResponse(NotificationPreference p) {
        return new PreferencesResponse(p.getUserId(), p.isEmailEnabled(), p.isSmsEnabled(), p.isPushEnabled(),
                p.isInAppEnabled(), p.isWebhookEnabled(), p.isDoNotDisturb(), p.getQuietHoursStart(), p.getQuietHoursEnd(),
                p.getTimezone(), p.getLocale(), p.getDefaultMode());
    }
}
