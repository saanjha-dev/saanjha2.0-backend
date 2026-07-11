package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;

/**
 * The one port every transport adapter implements. Nothing in {@code
 * service}, {@code rule}, or {@code controller} ever depends on a concrete
 * provider or the NotificationHub SDK directly - only on this interface,
 * resolved through {@link ProviderChainResolver}. This is what makes the
 * module brief's central requirement true: NotificationHub is "one provider
 * behind an abstraction", not the thing the module is built around.
 * <p>
 * Implementations must be side-effect-safe to retry (idempotent enough that
 * calling {@link #send} twice for the same {@link ProviderDispatchRequest}
 * is an acceptable (if not ideal) outcome, never a corruption risk) since
 * {@code NotificationDispatchService} will retry on ambiguous failures
 * (timeouts) where it cannot know if the first attempt actually delivered.
 */
public interface NotificationProvider {

    ProviderName name();

    NotificationChannel channel();

    /**
     * Perform exactly one delivery attempt. Implementations must not retry
     * internally - {@code NotificationDispatchService} owns all retry/backoff
     * policy so every attempt is uniformly recorded in {@code ProviderAttempt}.
     * Implementations should throw {@link ProviderDispatchException} on
     * failure rather than returning a failed result, so Resilience4j's
     * circuit breaker/retry annotations (applied at the call site) can see it.
     */
    ProviderDispatchResult send(ProviderDispatchRequest request) throws ProviderDispatchException;
}
