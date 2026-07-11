package com.saanjha.modules.notification.provider;

/** Thrown by any {@link NotificationProvider#send} on failure. Distinguishes retryable transport/provider failures (the default) from permanent ones (bad address, provider rejected the content) that should never be retried on the same or any other provider. */
public class ProviderDispatchException extends RuntimeException {

    private final boolean permanent;

    public ProviderDispatchException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public ProviderDispatchException(String message, boolean permanent) {
        this(message, permanent, null);
    }

    public boolean isPermanent() {
        return permanent;
    }
}
