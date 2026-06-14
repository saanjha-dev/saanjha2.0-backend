package com.saanjha.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RequestDTOs {
    private RequestDTOs() {} // Prevent instantiation

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Format must be a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
            @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
                    message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
            String password
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Format must be a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password,

            @NotBlank(message = "Device fingerprint is required")
            @Size(max = 255)
            String deviceId
    ) {}

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken,

            @NotBlank(message = "Device fingerprint is required")
            String deviceId
    ) {}

    public record VerifyOtpRequest(
            @NotBlank(message = "Email is required")
            @Email
            String email,

            @NotBlank(message = "OTP code is required")
            @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
            String otpCode
    ) {}

    public record VerifyResetOtpRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank(message = "OTP code is required")
            @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
            String otpCode
    ) {}

    public record ResetPasswordRequest(
            @NotBlank
            String resetToken,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
            @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
                    message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
            String newPassword
    ) {}

    public record ResendVerificationRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Format must be a valid email address")
            String email
    ) {}

    public record LogoutAllDevicesRequest(
            @NotBlank
            String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank
            @Email
            String email
    ) {}
}