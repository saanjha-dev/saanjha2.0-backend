package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.dto.ResponseDTOs;
import com.saanjha.modules.auth.entity.AuthRole;
import com.saanjha.modules.auth.entity.AuthSession;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.dto.RequestDTOs.*;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.event.AuthEvents.*;
import com.saanjha.modules.auth.repository.AuthRoleRepository;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.repository.RefreshTokenRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;

    private final StringRedisTemplate redisTemplate; // Replaces OtpRepository
    private final PasswordEncoder passwordEncoder;
    private final TokenRotationService tokenRotationService;
    private final EventPublisherService eventPublisher;
    private final PermissionCacheService permissionCacheService; // For eviction
    private final JwtProvider jwtProvider;

    @Transactional
    public void register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new AppException(ErrorCode.CONFLICT, "Email address is already in use.");
        }

        AuthUser user = new AuthUser();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEmailVerified(false);
        user.setStatus(AuthUser.AccountStatus.ACTIVE);

        // Fetch PBAC Role from Database
        AuthRole defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Default role not configured."));
        user.getRoles().add(defaultRole);

        userRepository.saveAndFlush(user); // Flush needed to satisfy FK constraints if needed elsewhere

        generateAndDispatchOtp(user.getEmail(), "EMAIL_VERIFICATION");
    }

    public void resendVerification(String email) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid request."));

        if (user.isEmailVerified()) {
            throw new AppException(ErrorCode.CONFLICT, "Email is already verified.");
        }

        generateAndDispatchOtp(user.getEmail(), "EMAIL_VERIFICATION");
    }

    @Transactional
    public void verifyEmail(VerifyOtpRequest req) {
        AuthUser user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid request."));

        validateAndConsumeOtp(req.email(), req.otpCode(), "EMAIL_VERIFICATION");
        userRepository.markEmailAsVerified(user.getId());
        eventPublisher.publish(new UserRegisteredEvent(user.getId(), user.getEmail(), Instant.now().toEpochMilli()));
    }

    @Transactional
    public AuthTokens login(LoginRequest req, String clientIp) {
        AuthUser user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid credentials."));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid credentials.");
        }
        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Please verify your email address.");
        }
        if (user.getStatus() != AuthUser.AccountStatus.ACTIVE) {
            // FIX (Admin module integration): distinguish the specific reason at the
            // error-code level — ACCOUNT_LOCKED/ACCOUNT_SUSPENDED already existed in
            // ErrorCode but were unused here; BANNED reuses ACCOUNT_SUSPENDED's code
            // (both are Admin-driven outcomes) with a clearer message.
            switch (user.getStatus()) {
                case LOCKED -> throw new AppException(ErrorCode.ACCOUNT_LOCKED, "This account has been locked due to suspicious activity.");
                case BANNED -> throw new AppException(ErrorCode.ACCOUNT_SUSPENDED, "This account has been permanently banned.");
                default -> throw new AppException(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended by an administrator.");
            }
        }

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setDeviceId(req.deviceId());
        session.setDeviceIp(clientIp);
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);

        return tokenRotationService.createTokenFamily(session, user);
    }

    public void requestPasswordReset(String email) {

        //System.out.println("REQUEST RECEIVED FOR: " + email);

        userRepository.findByEmail(email).ifPresent(user -> {

            System.out.println("USER FOUND: " + user.getEmail());
            System.out.println("STATUS: " + user.getStatus());

            if (user.getStatus() == AuthUser.AccountStatus.ACTIVE) {

                System.out.println("GENERATING OTP");

                generateAndDispatchOtp(
                        user.getEmail(),
                        "PASSWORD_RESET"
                );
            }
        });
    }

    @Transactional
    public void resetPassword(
            String resetToken,
            String newPassword
    ) {

        UUID userId =
                jwtProvider.validatePasswordResetToken(
                        resetToken
                );

        AuthUser user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.UNAUTHORIZED,
                                        "Invalid reset token."
                                ));

        userRepository.updatePassword(
                userId,
                passwordEncoder.encode(newPassword)
        );

        logoutAllDevicesInternal(userId);

        permissionCacheService.evictUserCache(userId);
    }

    private void logoutAllDevicesInternal(
            UUID userId
    ) {
        sessionRepository.deactivateAllUserSessions(
                userId
        );

        tokenRepository.revokeAllTokensForUser(
                userId
        );
    }

    @Transactional
    public ResponseDTOs.PasswordResetTokenResponse verifyPasswordResetOtp(
            VerifyResetOtpRequest request
    ) {

        AuthUser user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() ->
                        new AppException(
                                ErrorCode.UNAUTHORIZED,
                                "Invalid reset request."
                        ));

        validateAndConsumeOtp(
                request.email(),
                request.otpCode(),
                "PASSWORD_RESET"
        );

        String resetToken =
                jwtProvider.generatePasswordResetToken(
                        user.getId(),
                        user.getEmail()
                );

        return new ResponseDTOs.PasswordResetTokenResponse(
                resetToken,
                300
        );
    }

    @Transactional
    public void logoutCurrentSession(String rawRefreshToken, String deviceId) {
        UUID sessionId = tokenRotationService.extractSessionIdFromToken(rawRefreshToken);
        sessionRepository.deactivateSession(sessionId);
        tokenRepository.revokeAllTokensForSession(sessionId);
    }

    @Transactional
    public void logoutAllDevices(
            UUID userId,
            String password
    ) {

        AuthUser user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.UNAUTHORIZED,
                                        "User not found."
                                ));

        if (!passwordEncoder.matches(
                password,
                user.getPasswordHash()
        )) {

            throw new AppException(
                    ErrorCode.UNAUTHORIZED,
                    "Password verification failed."
            );
        }

        logoutAllDevicesInternal(userId);
    }

    // ========================================================================
    // REDIS OTP UTILITIES (No Postgres Bloat, Auto Expiration)
    // ========================================================================

    private void generateAndDispatchOtp(String email, String purpose) {
        int secureNumber = 100000 + SECURE_RANDOM.nextInt(900000);
        String rawOtp = String.valueOf(secureNumber);

        // Treat OTPs exactly like passwords in cache
        String hashedOtp = passwordEncoder.encode(rawOtp);
        String redisKey = "auth:otp:" + purpose + ":" + email;

        // Overwrites any existing pending OTP. Strict 5-minute TTL.
        redisTemplate.opsForValue().set(redisKey, hashedOtp, Duration.ofMinutes(5));

        eventPublisher.publish(new OtpGeneratedEvent(email, rawOtp, purpose));
    }

    private void validateAndConsumeOtp(String email, String rawOtp, String purpose) {
        String redisKey = "auth:otp:" + purpose + ":" + email;
        String storedHash = redisTemplate.opsForValue().get(redisKey);

        if (storedHash == null || !passwordEncoder.matches(rawOtp, storedHash)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "OTP is invalid or has expired.");
        }

        redisTemplate.delete(redisKey); // Single-use guarantee
    }
}