package com.saanjha.modules.auth.controller;

import com.saanjha.modules.auth.dto.RequestDTOs.*;
import com.saanjha.modules.auth.dto.ResponseDTOs;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.service.AuthService;
import com.saanjha.modules.auth.service.TokenRotationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.idempotency.Idempotent;
import com.saanjha.shared.ratelimit.RateLimit;

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

import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Identity verification, session management, and cryptography")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final TokenRotationService tokenRotationService;

    // ========================================================================
    // PUBLIC ENDPOINTS
    // ========================================================================

    @PostMapping("/register")
    @Idempotent(action = "register")
    @RateLimit(action = "register", baseLimit = 3, baseTimeSeconds = 300) // Stricter: 3 attempts per 5 mins
    @Operation(summary = "Register a new user", description = "Creates identity and dispatches async email OTP. Requires an Idempotency-Key header.")
    public ResponseEntity<ApiEnvelope<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiEnvelope.success("Registration successful. Please check your email for the verification code."));
    }

    @PostMapping("/resend-verification")
    @RateLimit(action = "resend-verification", baseLimit = 3, baseTimeSeconds = 60)
    @Operation(summary = "Resend Verification Email", description = "Generates a new Redis OTP for an unverified account.")
    public ResponseEntity<ApiEnvelope<String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiEnvelope.success("If the account is unverified, a new code has been sent."));
    }

    @PostMapping("/verify-email")
    @RateLimit(action = "verify-email", baseLimit = 5) // Defaults to 60 seconds
    @Operation(summary = "Verify Email via OTP", description = "Consumes the 6-digit Redis OTP to activate the account.")
    public ResponseEntity<ApiEnvelope<String>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiEnvelope.success("Email verified successfully. You may now log in."));
    }

    @PostMapping("/login")
    @RateLimit(action = "login", baseLimit = 5, errorMessage = "Too many login attempts")
    @Operation(summary = "Authenticate Device", description = "Verifies credentials and issues a cryptographic token family.")
    public ResponseEntity<ApiEnvelope<AuthTokens>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr(); // IP only needed for session tracking now
        AuthTokens tokens = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiEnvelope.success(tokens));
    }

    @PostMapping("/refresh")
    @RateLimit(action = "refresh", baseLimit = 10, baseTimeSeconds = 60) // explicitly setting the time limits the Redis duration
    @Operation(summary = "Rotate Session Tokens", description = "Consumes refresh token to issue a new short-lived access token.")
    public ResponseEntity<ApiEnvelope<AuthTokens>> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpServletRequest) {
        AuthTokens tokens = tokenRotationService.rotate(request.refreshToken(), request.deviceId(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiEnvelope.success(tokens));
    }

    @PostMapping("/forgot-password")
    @RateLimit(action = "forgot-password", baseLimit = 3, baseTimeSeconds = 120)
    @Operation(summary = "Request Password Reset", description = "Dispatches a secure OTP to the provided email if the account exists.")
    public ResponseEntity<ApiEnvelope<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiEnvelope.success("If an active account matches that email, a reset code has been sent."));
    }

    @PostMapping("/verify-reset-otp")
    @RateLimit(action = "verify-reset-otp", baseLimit = 5)
    @Operation(summary = "Verify Password Reset OTP", description = "Validates the reset OTP and issues a secure, short-lived reset token.")
    public ResponseEntity<ApiEnvelope<ResponseDTOs.PasswordResetTokenResponse>> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(authService.verifyPasswordResetOtp(request)));
    }

    @PostMapping("/reset-password")
    @RateLimit(action = "reset-password", baseLimit = 3)
    @Operation(summary = "Execute Password Reset", description = "Consumes the secure reset token and applies the new password.")
    public ResponseEntity<ApiEnvelope<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.ok(ApiEnvelope.success("Password reset successfully."));
    }

    // ========================================================================
    // PROTECTED ENDPOINTS (Requires valid Access Token)
    // ========================================================================

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Terminate Current Session", description = "Revokes the active refresh token.")
    public ResponseEntity<ApiEnvelope<String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logoutCurrentSession(request.refreshToken(), request.deviceId());
        return ResponseEntity.ok(ApiEnvelope.success("Session terminated securely."));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "logout-all", baseLimit = 3)
    @Operation(summary = "Terminate All Sessions", description = "Scorched earth: Logs the user out of every device globally.")
    public ResponseEntity<ApiEnvelope<String>> logoutAllDevices(@Valid @RequestBody LogoutAllDevicesRequest request) {
        authService.logoutAllDevices(getAuthenticatedUserId(), request.password());
        return ResponseEntity.ok(ApiEnvelope.success("All sessions terminated."));
    }

    @PostMapping("/change-password/request")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "change-password-request", baseLimit = 3, baseTimeSeconds = 60)
    @Operation(summary = "Request Password Change", description = "Sends an OTP to the user's email to authorize a password change.")
    public ResponseEntity<ApiEnvelope<String>> requestPasswordChange() {
        authService.requestPasswordChange(getAuthenticatedUserId());
        return ResponseEntity.ok(ApiEnvelope.success("Verification code sent to your email."));
    }

    @PostMapping("/change-password/verify")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "change-password-verify", baseLimit = 5)
    @Operation(summary = "Verify Password Change OTP", description = "Validates the OTP and updates the password, logging out all devices.")
    public ResponseEntity<ApiEnvelope<String>> verifyPasswordChange(@Valid @RequestBody ChangePasswordVerifyRequest request) {
        authService.verifyPasswordChange(getAuthenticatedUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Password changed successfully. You will be logged out everywhere."));
    }

    @PostMapping("/change-mfa/request")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "mfa-setup-request", baseLimit = 3, baseTimeSeconds = 60)
    @Operation(summary = "Request MFA Setup", description = "Sends an OTP to the user's email to authorize MFA setup.")
    public ResponseEntity<ApiEnvelope<String>> requestMfaSetup() {
        authService.requestMfaSetup(getAuthenticatedUserId());
        return ResponseEntity.ok(ApiEnvelope.success("Verification code sent to your email."));
    }

    @PostMapping("/change-mfa/verify")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "mfa-setup-verify", baseLimit = 5)
    @Operation(summary = "Verify MFA Setup OTP", description = "Validates the OTP and returns the TOTP secret and QR code URI.")
    public ResponseEntity<ApiEnvelope<ResponseDTOs.MfaSetupResponse>> verifyMfaSetup(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(authService.verifyMfaSetup(getAuthenticatedUserId(), request.otpCode())));
    }

    @PostMapping("/change-mfa/enable")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "mfa-setup-enable", baseLimit = 5)
    @Operation(summary = "Enable MFA", description = "Verifies the TOTP code and fully enables MFA for the account.")
    public ResponseEntity<ApiEnvelope<String>> enableMfa(@Valid @RequestBody EnableMfaRequest request) {
        authService.enableMfa(getAuthenticatedUserId(), request.totpCode());
        return ResponseEntity.ok(ApiEnvelope.success("MFA has been successfully enabled."));
    }

    @GetMapping("/mfa/status")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "mfa-status", baseLimit = 10)
    @Operation(summary = "Get MFA Status", description = "Returns whether MFA is enabled for the current account.")
    public ResponseEntity<ApiEnvelope<ResponseDTOs.MfaStatusResponse>> getMfaStatus() {
        return ResponseEntity.ok(ApiEnvelope.success(authService.getMfaStatus(getAuthenticatedUserId())));
    }

    @PostMapping("/change-mfa/disable")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(action = "mfa-setup-disable", baseLimit = 5)
    @Operation(summary = "Disable MFA", description = "Verifies password and disables MFA for the account.")
    public ResponseEntity<ApiEnvelope<String>> disableMfa(@Valid @RequestBody DisableMfaRequest request) {
        authService.disableMfa(getAuthenticatedUserId(), request.password());
        return ResponseEntity.ok(ApiEnvelope.success("MFA has been successfully disabled."));
    }

    @PostMapping("/login/mfa")
    @RateLimit(action = "login-mfa", baseLimit = 5, errorMessage = "Too many login attempts")
    @Operation(summary = "Authenticate MFA", description = "Verifies TOTP and issues a cryptographic token family.")
    public ResponseEntity<ApiEnvelope<AuthTokens>> verifyLoginMfa(@Valid @RequestBody VerifyLoginMfaRequest request, HttpServletRequest httpServletRequest) {
        AuthTokens tokens = authService.verifyLoginMfa(request.mfaToken(), request.totpCode(), httpServletRequest.getRemoteAddr(), request.deviceId(), request.trustDevice());
        return ResponseEntity.ok(ApiEnvelope.success(tokens));
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Valid authentication is required.");
        }
        return UUID.fromString(authentication.getName());
    }
}