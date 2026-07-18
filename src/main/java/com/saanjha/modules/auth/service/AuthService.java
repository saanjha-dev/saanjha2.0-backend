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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    /**
     * A pre-computed bcrypt hash used only to pad timing for non-existent
     * accounts in {@link #login} - the plaintext behind it is irrelevant and
     * deliberately never used for anything else; it exists purely so a
     * failed comparison against it costs the same as a real one.
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder(10).encode("no-account-exists-for-this-timing-decoy");

    @Transactional
    public AuthTokens login(LoginRequest req, String clientIp) {
        Optional<AuthUser> maybeUser = userRepository.findByEmail(req.email());

        // FIX (hardening sprint, P0-3): previously, a non-existent email
        // threw immediately (a single fast DB lookup) while an existing
        // email always paid the ~100ms bcrypt comparison cost before
        // failing - an attacker measuring response time could enumerate
        // valid accounts without ever seeing a different error message.
        // Now every attempt runs exactly one bcrypt comparison, against the
        // real hash if the account exists or a fixed decoy hash if it
        // doesn't, so timing no longer reveals account existence.
        boolean passwordMatches = passwordEncoder.matches(
                req.password(),
                maybeUser.map(AuthUser::getPasswordHash).orElse(DUMMY_PASSWORD_HASH));

        AuthUser user = maybeUser.orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid credentials."));

        if (!passwordMatches) {
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
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getStatus() == AuthUser.AccountStatus.ACTIVE) {
                generateAndDispatchOtp(user.getEmail(), "PASSWORD_RESET");
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
        String redisKey = "auth:otp:" + purpose + ":" + email;

        // 1. Enforce a 60-second cooldown before allowing a new OTP generation
        Long expireSeconds = redisTemplate.getExpire(redisKey, java.util.concurrent.TimeUnit.SECONDS);

        // If TTL is greater than 240 seconds, the OTP was generated less than 60 seconds ago.
        if (expireSeconds != null && expireSeconds > 240) {
            long secondsToWait = expireSeconds - 240;
            throw new AppException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Please wait " + secondsToWait + " seconds before requesting a new OTP."
            );
        }

        // 2. Generate the new OTP
        int secureNumber = 100000 + SECURE_RANDOM.nextInt(900000);
        String rawOtp = String.valueOf(secureNumber);

        // Treat OTPs exactly like passwords in cache
        String hashedOtp = passwordEncoder.encode(rawOtp);

        // 3. Overwrite any existing pending OTP (that has passed the cooldown). Strict 5-minute TTL.
        redisTemplate.opsForValue().set(redisKey, hashedOtp, Duration.ofMinutes(5));

        eventPublisher.publish(new OtpGeneratedEvent(email, rawOtp, purpose));
    }

    /**
     * FIX (hardening sprint, P0-3): {@code @RateLimit} on the controller
     * layer keys attempts by CALLER (authenticated user ID, or now the raw
     * client IP - see {@code RateLimitAspect}). For OTP verification the
     * caller is always anonymous, so that limiter caps "how many guesses can
     * one IP make" - it does nothing to stop an attacker distributing guesses
     * for one specific victim's 6-digit OTP across many source IPs (cheap
     * and common via proxy pools/botnets) within the 5-minute TTL window.
     * This adds a second, TARGET-keyed limiter (by email+purpose, not by
     * caller) so a specific account's OTP is capped regardless of how many
     * distinct source IPs the guesses come from. Deliberately small and
     * scoped here rather than added as a new capability to the shared
     * {@code RateLimit} annotation/aspect, which has no concept of "key by a
     * field in the request body" - extending it would be a larger,
     * higher-risk change for a fix that only this one code path needs.
     */
    private static final int MAX_OTP_ATTEMPTS = 5;

    private void validateAndConsumeOtp(String email, String rawOtp, String purpose) {
        String redisKey = "auth:otp:" + purpose + ":" + email;
        String attemptsKey = "auth:otp-attempts:" + purpose + ":" + email;

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(5));
        }
        if (attempts != null && attempts > MAX_OTP_ATTEMPTS) {
            // Same generic message as an invalid/expired OTP - a distinct
            // "too many attempts" message here would let an attacker use the
            // error text itself to fingerprint whether the account/purpose
            // combination exists and has an active OTP in flight.
            throw new AppException(ErrorCode.UNAUTHORIZED, "OTP is invalid or has expired.");
        }

        String storedHash = redisTemplate.opsForValue().get(redisKey);

        if (storedHash == null || !passwordEncoder.matches(rawOtp, storedHash)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "OTP is invalid or has expired.");
        }

        redisTemplate.delete(redisKey); // Single-use guarantee
        redisTemplate.delete(attemptsKey); // Successful use resets the attempt counter
    }
}