package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.entity.AuthSession;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.entity.RefreshToken;
import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.repository.RefreshTokenRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TD13 (architecture-review.md §9.6 item 6 / technical-debt.md): the highest
 * security-surface module in the repository had zero tests. This is a first,
 * deliberately not exhaustive, pass focused on the business-logic paths of
 * {@code TokenRotationService.rotate} — the method carrying the reuse-
 * detection guarantee and the TD11 concurrency fix.
 *
 * NOTE ON WHAT THIS SUITE CANNOT COVER: the TD11 fix is a database-level
 * pessimistic lock (@Lock(PESSIMISTIC_WRITE) on the repository query).
 * Mockito mocks the repository, so it cannot exercise real row-locking
 * behavior — that guarantee needs a Testcontainers-backed concurrency test
 * (two threads calling rotate() with the same token against a real
 * PostgreSQL instance) as a follow-up, tracked separately. This suite proves
 * the *logic* is correct; a future integration test should prove the *lock*
 * actually serializes concurrent callers.
 */
@ExtendWith(MockitoExtension.class)
class TokenRotationServiceTest {

    @Mock private RefreshTokenRepository tokenRepository;
    @Mock private AuthSessionRepository sessionRepository;
    @Mock private AuthUserRepository userRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private EventPublisherService eventPublisher;

    private TokenRotationService tokenRotationService;

    private UUID sessionId;
    private UUID userId;
    private static final String DEVICE_ID = "device-abc";
    private static final String DEVICE_IP = "203.0.113.10";

    @BeforeEach
    void setUp() {
        tokenRotationService = new TokenRotationService(tokenRepository, sessionRepository, userRepository, jwtProvider, eventPublisher);
        ReflectionTestUtils.setField(tokenRotationService, "refreshExpirationMs", 604_800_000L);
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void rotate_happyPath_marksOldTokenUsedAndIssuesNewPair() {
        RefreshToken oldToken = freshToken();
        AuthSession session = activeSession();
        AuthUser user = user();

        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.of(oldToken));
        when(sessionRepository.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtProvider.getJwtExpirationMs()).thenReturn(900_000L);

        AuthTokens tokens = tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP);

        assertThat(oldToken.isUsed()).isTrue();
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        verify(tokenRepository, times(2)).save(any(RefreshToken.class)); // old token marked used, new token created
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rotate_reusedToken_killsSessionAndPublishesSuspiciousActivity() {
        RefreshToken usedToken = freshToken();
        usedToken.setUsed(true);
        AuthSession session = activeSession();

        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.of(usedToken));
        when(sessionRepository.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(sessionRepository).deactivateSession(sessionId);
        verify(tokenRepository).revokeAllTokensForSession(sessionId);
        verify(eventPublisher).publish(any());
    }

    @Test
    void rotate_revokedToken_isTreatedAsReuse() {
        RefreshToken revokedToken = freshToken();
        revokedToken.setRevoked(true);
        AuthSession session = activeSession();

        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.of(revokedToken));
        when(sessionRepository.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(eventPublisher).publish(any());
    }

    @Test
    void rotate_expiredToken_isRejectedBeforeReachingReuseLogic() {
        RefreshToken expiredToken = freshToken();
        expiredToken.setExpiresAt(Instant.now().minusSeconds(3600));
        AuthSession session = activeSession();

        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.of(expiredToken));
        when(sessionRepository.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rotate_deviceFingerprintMismatch_isRejected() {
        RefreshToken oldToken = freshToken();
        AuthSession session = activeSession();
        session.setDeviceId("a-different-device");

        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.of(oldToken));
        when(sessionRepository.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(tokenRepository.findByTokenHashForRotation(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenRotationService.rotate("raw-token", DEVICE_ID, DEVICE_IP))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private RefreshToken freshToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setSessionId(sessionId);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private AuthSession activeSession() {
        AuthSession session = new AuthSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setDeviceId(DEVICE_ID);
        session.setDeviceIp(DEVICE_IP);
        session.setActive(true);
        return session;
    }

    private AuthUser user() {
        AuthUser user = new AuthUser();
        user.setId(userId);
        user.setEmail("test@example.com");
        return user;
    }
}
