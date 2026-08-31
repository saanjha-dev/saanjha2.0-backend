package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.dto.ResponseDTOs;
import com.saanjha.modules.auth.entity.AuthRole;
import com.saanjha.modules.auth.entity.AuthSession;
import com.saanjha.modules.auth.entity.AuthTrustedDevice;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.dto.RequestDTOs.*;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.event.AuthEvents.*;
import com.saanjha.modules.auth.repository.AuthRoleRepository;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.repository.AuthTrustedDeviceRepository;
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
import java.time.temporal.ChronoUnit;
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
    private final AuthTrustedDeviceRepository authTrustedDeviceRepository;

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

    @Transactional
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

        if (user.isMfaEnabled()) {
            Optional<AuthTrustedDevice> trustedDeviceOpt = authTrustedDeviceRepository.findByUserIdAndDeviceId(user.getId(), req.deviceId());
            if (trustedDeviceOpt.isPresent()) {
                AuthTrustedDevice trustedDevice = trustedDeviceOpt.get();
                if (trustedDevice.getExpiresAt().isAfter(Instant.now())) {
                    AuthSession session = new AuthSession();
                    session.setUserId(user.getId());
                    session.setDeviceId(req.deviceId());
                    session.setDeviceIp(clientIp);
                    session.setLastActivityAt(Instant.now());
                    sessionRepository.save(session);
                    return tokenRotationService.createTokenFamily(session, user);
                } else {
                    authTrustedDeviceRepository.delete(trustedDevice);
                }
            }

            String mfaToken = jwtProvider.generatePasswordResetToken(user.getId(), user.getEmail()); // Reuse short lived token logic
            return AuthTokens.requireMfa(mfaToken);
        }

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setDeviceId(req.deviceId());
        session.setDeviceIp(clientIp);
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);

        return tokenRotationService.createTokenFamily(session, user);
    }

    @Transactional
    public AuthTokens oauthLogin(String email, String authProvider, String providerId, String clientIp) {
        // Find existing user by email or by providerId
        Optional<AuthUser> maybeUser = userRepository.findByEmail(email);

        AuthUser user;
        if (maybeUser.isPresent()) {
            user = maybeUser.get();
            // If the user already exists (maybe via local signup), we can link the OAuth provider.
            if ("LOCAL".equals(user.getAuthProvider())) {
                user.setAuthProvider(authProvider);
                user.setProviderId(providerId);
                userRepository.save(user);
            }
        } else {
            // Register new user via OAuth
            user = new AuthUser();
            user.setEmail(email);
            user.setEmailVerified(true); // OAuth emails are verified by the provider
            user.setAuthProvider(authProvider);
            user.setProviderId(providerId);
            
            // Assign default role (e.g. ROLE_USER)
            AuthRole memberRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Default role missing"));
            user.getRoles().add(memberRole);
            
            user = userRepository.save(user);
            eventPublisher.publish(new UserRegisteredEvent(user.getId(), user.getEmail(), Instant.now().toEpochMilli()));
        }

        if (user.getStatus() != AuthUser.AccountStatus.ACTIVE) {
            switch (user.getStatus()) {
                case LOCKED -> throw new AppException(ErrorCode.ACCOUNT_LOCKED, "This account has been locked due to suspicious activity.");
                case BANNED -> throw new AppException(ErrorCode.ACCOUNT_SUSPENDED, "This account has been permanently banned.");
                default -> throw new AppException(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended by an administrator.");
            }
        }

        // Create Session
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setDeviceId("OAUTH2_DEVICE"); // Using a generic identifier since we don't have client fingerprint in the callback
        session.setDeviceIp(clientIp);
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);

        return tokenRotationService.createTokenFamily(session, user);
    }

    @Transactional
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

        authTrustedDeviceRepository.deleteByUserId(userId);
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

    @Transactional
    public void requestPasswordChange(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));
        
        if (user.getStatus() == AuthUser.AccountStatus.ACTIVE) {
            generateAndDispatchOtp(user.getEmail(), "PASSWORD_CHANGE");
        }
    }

    @Transactional
    public void verifyPasswordChange(UUID userId, ChangePasswordVerifyRequest request) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));

        validateAndConsumeOtp(user.getEmail(), request.otpCode(), "PASSWORD_CHANGE");

        userRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));

        logoutAllDevicesInternal(userId);
        permissionCacheService.evictUserCache(userId);
    }

    @Transactional
    public void requestMfaSetup(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));
        if (user.getStatus() == AuthUser.AccountStatus.ACTIVE) {
            generateAndDispatchOtp(user.getEmail(), "MFA_SETUP");
        }
    }

    @Transactional
    public ResponseDTOs.MfaSetupResponse verifyMfaSetup(UUID userId, String emailOtp) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));

        validateAndConsumeOtp(user.getEmail(), emailOtp, "MFA_SETUP");

        dev.samstevens.totp.secret.SecretGenerator secretGenerator = new dev.samstevens.totp.secret.DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        userRepository.save(user);

        dev.samstevens.totp.qr.QrData data = new dev.samstevens.totp.qr.QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("Saanjha")
                .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        dev.samstevens.totp.qr.QrGenerator generator = new dev.samstevens.totp.qr.ZxingPngQrGenerator();
        byte[] imageData;
        try {
            imageData = generator.generate(data);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate QR code.");
        }
        String mimeType = generator.getImageMimeType();
        String dataUri = dev.samstevens.totp.util.Utils.getDataUriForImage(imageData, mimeType);

        return new ResponseDTOs.MfaSetupResponse(secret, dataUri);
    }

    @Transactional
    public void enableMfa(UUID userId, String totpCode) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));

        if (user.getMfaSecret() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "MFA setup not initialized.");
        }

        dev.samstevens.totp.time.TimeProvider timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        dev.samstevens.totp.code.CodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        dev.samstevens.totp.code.CodeVerifier verifier = new dev.samstevens.totp.code.DefaultCodeVerifier(codeGenerator, timeProvider);

        if (!verifier.isValidCode(user.getMfaSecret(), totpCode)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid TOTP code.");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public ResponseDTOs.MfaStatusResponse getMfaStatus(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));
        return new ResponseDTOs.MfaStatusResponse(user.isMfaEnabled());
    }

    @Transactional
    public void disableMfa(UUID userId, String password) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "User not found."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Password verification failed.");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
    }

    @Transactional
    public AuthTokens verifyLoginMfa(String mfaToken, String totpCode, String clientIp, String deviceId, Boolean trustDevice) {
        UUID userId = jwtProvider.validatePasswordResetToken(mfaToken); // Reusing logic

        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid MFA token."));

        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "MFA is not enabled for this account.");
        }

        dev.samstevens.totp.time.TimeProvider timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        dev.samstevens.totp.code.CodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        dev.samstevens.totp.code.CodeVerifier verifier = new dev.samstevens.totp.code.DefaultCodeVerifier(codeGenerator, timeProvider);

        if (!verifier.isValidCode(user.getMfaSecret(), totpCode)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid TOTP code.");
        }

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setDeviceId(deviceId); 
        session.setDeviceIp(clientIp);
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);

        if (Boolean.TRUE.equals(trustDevice)) {
            Optional<AuthTrustedDevice> existingDevice = authTrustedDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId);
            AuthTrustedDevice trustedDevice = existingDevice.orElseGet(AuthTrustedDevice::new);
            trustedDevice.setUser(user);
            trustedDevice.setDeviceId(deviceId);
            trustedDevice.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            authTrustedDeviceRepository.save(trustedDevice);
        }

        return tokenRotationService.createTokenFamily(session, user);
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