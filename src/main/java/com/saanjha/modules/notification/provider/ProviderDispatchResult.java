package com.saanjha.modules.notification.provider;

/** The outcome of one successful {@link NotificationProvider#send} call. Failures are exceptions, not results - see the interface javadoc. */
public record ProviderDispatchResult(
        boolean delivered,       // true = provider confirms final delivery synchronously (e.g. IN_APP write); false = accepted for async delivery (most external providers - final status arrives via webhook or is never confirmed)
        Integer providerStatusCode,
        String providerMessageId
) {
    public static ProviderDispatchResult accepted(Integer statusCode, String messageId) {
        return new ProviderDispatchResult(false, statusCode, messageId);
    }

    public static ProviderDispatchResult delivered(Integer statusCode, String messageId) {
        return new ProviderDispatchResult(true, statusCode, messageId);
    }
}
