package com.saanjha.modules.auth.event;

import java.util.UUID;

public final class AuthEvents {
    private AuthEvents() {}

    public record UserRegisteredEvent(UUID userId, String email, long timestamp) {}

    public record SessionRevokedEvent(UUID userId, UUID sessionId, String deviceId, long timestamp) {}

    public record SuspiciousActivityDetectedEvent(UUID userId, String ipAddress, String reason, long timestamp) {}

    // The specific OTP event listened to by the Email Service
    public record OtpGeneratedEvent(String email, String rawOtp, String purpose) {}
}