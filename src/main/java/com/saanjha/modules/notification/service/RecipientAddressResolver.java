package com.saanjha.modules.notification.service;

import com.saanjha.modules.auth.service.AuthContactProvider;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationPreference;
import com.saanjha.modules.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the actual address/token/URL a channel needs to dispatch to.
 * Two channels are honestly, currently unresolvable for most users, and this
 * class says so explicitly rather than guessing:
 * <ul>
 *   <li><b>SMS</b> - no module in this codebase captures a phone number
 *       anywhere (checked {@code UserProfile}, {@code AuthUser}). Wiring
 *       this up is future work belonging to whichever module should own
 *       phone numbers (most likely {@code user}), not something Notification
 *       can fabricate.</li>
 *   <li><b>PUSH</b> - no device/push-token registry exists yet (no mobile
 *       app registration concept anywhere in the repo). Same conclusion.</li>
 * </ul>
 * Both channels are fully wired end-to-end at the provider/resilience layer
 * (module brief's "Future Ready" extensibility goal) and will work the
 * moment a real address source exists - see the module's Future Extension
 * Points in the final report. Until then, {@link #resolve} returning empty
 * for these means {@code NotificationOrchestrationService} simply never
 * creates a delivery row for that channel rather than creating one destined
 * to fail forever.
 */
@Component
@RequiredArgsConstructor
public class RecipientAddressResolver {

    private final AuthContactProvider authContactProvider;
    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public Optional<String> resolve(UUID userId, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> authContactProvider.getVerifiedEmail(userId);
            case IN_APP -> Optional.of(userId.toString());
            case WEBHOOK -> preferenceRepository.findByUserId(userId)
                    .map(NotificationPreference::getWebhookUrl)
                    .filter(url -> url != null && !url.isBlank());
            case SMS, PUSH -> Optional.empty(); // See class javadoc.
        };
    }
}
