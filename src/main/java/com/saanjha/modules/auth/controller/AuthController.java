package com.saanjha.modules.auth.controller;

import com.saanjha.modules.auth.dto.RequestDTOs.*;
import com.saanjha.modules.auth.dto.ResponseDTOs;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.service.AuthService;
import com.saanjha.modules.auth.service.TokenRotationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
//@CrossOrigin("*")
@Tag(name = "1. Authentication", description = "Identity verification, session management, and cryptography")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final TokenRotationService tokenRotationService;

    // Redis-backed distributed rate limiting components
    private final LettuceBasedProxyManager<byte[]> proxyManager;
    private final BucketConfiguration strictAuthRateLimit;

    // ========================================================================
    // PUBLIC ENDPOINTS
    // ========================================================================

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates identity and dispatches async email OTP.")
    public ResponseEntity<ApiEnvelope<String>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpServletRequest) {
        enforceRateLimit(getClientIp(httpServletRequest), "register");
        authService.register(request);
        return ResponseEntity.ok(ApiEnvelope.success("Registration successful. Please check your email for the verification code."));
    }


    @PostMapping("/resend-verification")
    @Operation(summary = "Resend Verification Email", description = "Generates a new Redis OTP for an unverified account.")
    public ResponseEntity<ApiEnvelope<String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        enforceRateLimit(getClientIp(servletRequest), "resend-verification");

        authService.resendVerification(request.email());

        return ResponseEntity.ok(ApiEnvelope.success("If the account is unverified, a new code has been sent."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify Email via OTP", description = "Consumes the 6-digit Redis OTP to activate the account.")
    public ResponseEntity<ApiEnvelope<String>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpServletRequest) {
        enforceRateLimit(getClientIp(httpServletRequest), "verify-email");
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiEnvelope.success("Email verified successfully. You may now log in."));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate Device", description = "Verifies credentials and issues a cryptographic token family.")
    public ResponseEntity<ApiEnvelope<AuthTokens>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        String clientIp = getClientIp(httpServletRequest);
        enforceRateLimit(clientIp, "login");
        AuthTokens tokens = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiEnvelope.success(tokens));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate Session Tokens", description = "Consumes refresh token to issue a new short-lived access token and rotates the refresh token.")
    public ResponseEntity<ApiEnvelope<AuthTokens>> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpServletRequest) {
        AuthTokens tokens = tokenRotationService.rotate(request.refreshToken(), request.deviceId(), getClientIp(httpServletRequest));
        return ResponseEntity.ok(ApiEnvelope.success(tokens));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request Password Reset", description = "Dispatches a secure OTP to the provided email if the account exists.")
    public ResponseEntity<ApiEnvelope<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        enforceRateLimit(getClientIp(httpServletRequest), "forgot-password");
        authService.requestPasswordReset(request.email());

        return ResponseEntity.ok(ApiEnvelope.success("If an active account matches that email, a reset code has been sent."));
    }

    @PostMapping("/verify-reset-otp")
    @Operation(summary = "Verify Password Reset OTP", description = "Validates the reset OTP and issues a secure, short-lived reset token.")
    public ResponseEntity<ApiEnvelope<ResponseDTOs.PasswordResetTokenResponse>> verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequest request,
            HttpServletRequest servletRequest
    ) {
        enforceRateLimit(getClientIp(servletRequest), "verify-reset-otp");

        return ResponseEntity.ok(ApiEnvelope.success(authService.verifyPasswordResetOtp(request)));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Execute Password Reset", description = "Consumes the secure reset token, applies the new password, evicts PBAC cache, and revokes all active sessions globally.")
    public ResponseEntity<ApiEnvelope<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        enforceRateLimit(getClientIp(servletRequest), "reset-password");
        authService.resetPassword(request.resetToken(), request.newPassword());

        return ResponseEntity.ok(ApiEnvelope.success("Password reset successfully."));
    }

    // ========================================================================
    // PROTECTED ENDPOINTS (Requires valid Access Token)
    // ========================================================================

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Terminate Current Session", description = "Revokes the active refresh token and terminates the current device session.")
    public ResponseEntity<ApiEnvelope<String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logoutCurrentSession(request.refreshToken(), request.deviceId());
        return ResponseEntity.ok(ApiEnvelope.success("Session terminated securely."));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Terminate All Sessions", description = "Scorched earth: Logs the user out of every device globally. Requires password confirmation.")
    public ResponseEntity<ApiEnvelope<String>> logoutAllDevices(
            @Valid @RequestBody LogoutAllDevicesRequest request
    ) {
        authService.logoutAllDevices(getAuthenticatedUserId(), request.password());
        return ResponseEntity.ok(ApiEnvelope.success("All sessions terminated."));
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private void enforceRateLimit(String ipAddress, String action) {
        String bucketKey = "rate_limit:" + action + ":" + ipAddress;
        Bucket bucket = proxyManager.builder()
                .build(bucketKey.getBytes(StandardCharsets.UTF_8), strictAuthRateLimit);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit breached for IP {} on action {}", ipAddress, action);
            throw new AppException(ErrorCode.TOO_MANY_REQUESTS, "You are attempting this action too frequently. Please wait a minute.");
        }
    }

    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Valid authentication is required.");
        }
        return UUID.fromString(authentication.getName());
    }
}