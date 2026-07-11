package com.saanjha.modules.notification.webhook;

import java.util.UUID;

/** Parsed from the callback body after signature verification. {@code status} is one of DELIVERED / READ / FAILED. */
public record ProviderCallbackPayload(
        UUID deliveryId,
        String status,
        String errorMessage
) {}
