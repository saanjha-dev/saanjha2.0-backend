package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.entity.AuthSession;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.entity.RefreshToken;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;

import com.saanjha.modules.auth.event.AuthEvents.SuspiciousActivityDetectedEvent;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.repository.RefreshTokenRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenRotationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRotationService.class);

    private final RefreshTokenRepository tokenRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthUserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final EventPublisherService eventPublisher; // The custom wrapper we made earlier

    @Value("${app.jwt.refresh-expiration-ms:604800000}") // Default 7 Days
    private long refreshExpirationMs;

    /**
     * Issues a brand new refresh token family for a new login.
     */
    @Transactional
    public AuthTokens createTokenFamily(AuthSession session, AuthUser user) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setSessionId(session.getId());
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));

        tokenRepository.save(refreshToken);

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());

        return new AuthTokens(accessToken, rawRefreshToken, jwtProvider.getJwtExpirationMs());
    }

    /**
     * The core rotation logic. Validates the old token, destroys it, and issues a new pair.
     */
    @Transactional
    public AuthTokens rotate(String rawRefreshToken, String deviceId, String deviceIp) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken oldToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid refresh token."));

        AuthSession session = sessionRepository.findByIdAndActiveTrue(oldToken.getSessionId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Session expired or terminated."));

        // CRITICAL: Token Reuse Detection (Replay Attack)
        if (oldToken.isUsed() || oldToken.isRevoked()) {
            log.error("COMPROMISE DETECTED: Attempt to reuse old refresh token for User ID: {}", session.getUserId());

            // 1. Kill the active session
            sessionRepository.deactivateSession(session.getId());
            // 2. Kill the entire token family
            tokenRepository.revokeAllTokensForSession(session.getId());
            // 3. Alert the system
            eventPublisher.publish(new SuspiciousActivityDetectedEvent(
                    session.getUserId(), deviceIp, "Refresh Token Reuse", Instant.now().toEpochMilli()));

            throw new AppException(ErrorCode.FORBIDDEN, "Security violation detected. All sessions terminated. Please log in again.");
        }

        // Standard validation
        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token has expired. Please log in again.");
        }
        if (!session.getDeviceId().equals(deviceId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Device fingerprint mismatch.");
        }

        // 1. Invalidate old token
        oldToken.setUsed(true);
        tokenRepository.save(oldToken);

        // 2. Update Session Activity
        sessionRepository.updateLastActivity(session.getId(), Instant.now());

        // Ensure IP is current
        if (!session.getDeviceIp().equals(deviceIp)) {
            session.setDeviceIp(deviceIp);
            sessionRepository.save(session);
        }

        // 3. Generate New Tokens
        AuthUser user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));

        String newRawRefreshToken = UUID.randomUUID().toString();

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(hashToken(newRawRefreshToken));
        newToken.setSessionId(session.getId());
        newToken.setParentTokenId(oldToken.getId()); // Maintain the cryptograhic chain
        newToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));

        tokenRepository.save(newToken);

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());

        return new AuthTokens(newAccessToken, newRawRefreshToken, jwtProvider.getJwtExpirationMs());
    }

    /**
     * Securely extracts the Session ID from a raw refresh token.
     * It hashes the token to prevent raw exposure and looks it up in the database.
     */
    public UUID extractSessionIdFromToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid refresh token."));

        return token.getSessionId();
    }
    /**
     * Hashes the raw UUID refresh token using SHA-256 to prevent DB read-compromise.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Cryptographic failure.");
        }
    }
}