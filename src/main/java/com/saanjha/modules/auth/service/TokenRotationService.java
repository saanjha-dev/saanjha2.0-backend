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
    private final EventPublisherService eventPublisher;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

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
     * FIX (TD11, architecture-review.md §7 item 2): the token read is now a
     * pessimistic-write-locked query (see {@code RefreshTokenRepository
     * .findByTokenHashForRotation}), not a plain unlocked read. Two
     * near-simultaneous rotate() calls against the same refresh token now
     * fully serialize on this row: the first to acquire the lock proceeds
     * through the reuse check, marks the token used, and commits (releasing
     * the lock); the second then acquires the lock, re-reads inside its own
     * transaction, and correctly observes {@code used = true} — triggering
     * genuine reuse detection instead of a race where both could succeed.
     */
    @Transactional
    public AuthTokens rotate(String rawRefreshToken, String deviceId, String deviceIp) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken oldToken = tokenRepository.findByTokenHashForRotation(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid refresh token."));

        AuthSession session = sessionRepository.findByIdAndActiveTrue(oldToken.getSessionId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Session expired or terminated."));

        // CRITICAL: Token Reuse Detection (Replay Attack)
        if (oldToken.isUsed() || oldToken.isRevoked()) {
            log.error("COMPROMISE DETECTED: Attempt to reuse old refresh token for User ID: {}", session.getUserId());

            sessionRepository.deactivateSession(session.getId());
            tokenRepository.revokeAllTokensForSession(session.getId());
            eventPublisher.publish(new SuspiciousActivityDetectedEvent(
                    session.getUserId(), deviceIp, "Refresh Token Reuse", Instant.now().toEpochMilli()));

            throw new AppException(ErrorCode.FORBIDDEN, "Security violation detected. All sessions terminated. Please log in again.");
        }

        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token has expired. Please log in again.");
        }
        if (!session.getDeviceId().equals(deviceId)) {
            if ("OAUTH2_DEVICE".equals(session.getDeviceId())) {
                // First refresh after OAuth login: bind the session to the actual device ID
                session.setDeviceId(deviceId);
                sessionRepository.save(session);
            } else {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Device fingerprint mismatch.");
            }
        }

        oldToken.setUsed(true);
        tokenRepository.save(oldToken);

        sessionRepository.updateLastActivity(session.getId(), Instant.now());

        if (!session.getDeviceIp().equals(deviceIp)) {
            session.setDeviceIp(deviceIp);
            sessionRepository.save(session);
        }

        AuthUser user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));

        String newRawRefreshToken = UUID.randomUUID().toString();

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(hashToken(newRawRefreshToken));
        newToken.setSessionId(session.getId());
        newToken.setParentTokenId(oldToken.getId());
        newToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));

        tokenRepository.save(newToken);

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());

        return new AuthTokens(newAccessToken, newRawRefreshToken, jwtProvider.getJwtExpirationMs());
    }

    public UUID extractSessionIdFromToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid refresh token."));

        return token.getSessionId();
    }

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
