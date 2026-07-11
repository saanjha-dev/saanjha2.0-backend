package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderHealth;
import com.saanjha.modules.notification.entity.ProviderName;
import com.saanjha.modules.notification.repository.ProviderHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * See {@link ProviderHealth}'s javadoc for why this exists alongside
 * Resilience4j's own in-memory circuit state rather than instead of it.
 * {@code REQUIRES_NEW} so a health-row update never participates in (and can
 * never roll back alongside) the delivery-dispatch transaction it's
 * reporting on - health bookkeeping failing must never be why a
 * notification's own status update rolls back.
 */
@Component
@RequiredArgsConstructor
public class ProviderHealthTracker {

    private final ProviderHealthRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(ProviderName provider, NotificationChannel channel) {
        ProviderHealth health = repository.findById(key(provider, channel))
                .orElseGet(() -> ProviderHealth.init(provider, channel));
        health.recordSuccess();
        repository.save(health);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(ProviderName provider, NotificationChannel channel, String error) {
        ProviderHealth health = repository.findById(key(provider, channel))
                .orElseGet(() -> ProviderHealth.init(provider, channel));
        health.recordFailure(error);
        repository.save(health);
    }

    private static String key(ProviderName provider, NotificationChannel channel) {
        return provider.name() + ":" + channel.name();
    }
}
