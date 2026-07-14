package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.config.RsaKeyProperties;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Zero prior coverage for this class (grep confirmed no existing test file).
 * Uses a real, freshly-generated RSA keypair (not the checked-in dev
 * keys/mocks) so signature verification is exercised for real, not stubbed
 * away - the whole point of these tests is proving the cryptographic
 * boundary actually holds.
 */
class JwtProviderTest {

    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;

    private JwtProvider jwtProvider;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    @BeforeEach
    void setUp() {
        RsaKeyProperties keys = new RsaKeyProperties(publicKey, privateKey);
        jwtProvider = new JwtProvider(keys);
        ReflectionTestUtils.setField(jwtProvider, "jwtExpirationMs", 900_000L);
    }

    @Test
    void generateAndValidateAccessToken_roundTripsToTheSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.generateAccessToken(userId, "user@example.com");
        UUID resolved = jwtProvider.validateAndGetUserId(token);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void validateAndGetUserId_rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1800)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(expired))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validateAndGetUserId_rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId("not.a.jwt"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validateAndGetUserId_rejectsTamperedSignature() {
        String token = jwtProvider.generateAccessToken(UUID.randomUUID(), "user@example.com");
        // Flip a character in the signature segment (last part after the final '.').
        int lastDot = token.lastIndexOf('.');
        String tampered = token.substring(0, lastDot + 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A')
                + token.substring(lastDot + 2);

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(tampered))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validateAndGetUserId_rejectsTokenSignedByADifferentKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair foreignKeyPair = generator.generateKeyPair();

        String tokenFromAnotherIssuer = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(foreignKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(tokenFromAnotherIssuer))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    /**
     * FIX-adjacent regression guard: {@code JwtProvider} deliberately tags
     * every token with a {@code type}/{@code purpose} claim so an access
     * token can never be replayed as a password-reset token or vice versa,
     * even though both are RS256-signed by the same keypair. This proves
     * that segregation actually holds both directions.
     */
    @Test
    void passwordResetToken_isRejectedByAccessTokenValidation() {
        String resetToken = jwtProvider.generatePasswordResetToken(UUID.randomUUID(), "user@example.com");

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(resetToken))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void accessToken_isRejectedByPasswordResetTokenValidation() {
        String accessToken = jwtProvider.generateAccessToken(UUID.randomUUID(), "user@example.com");

        assertThatThrownBy(() -> jwtProvider.validatePasswordResetToken(accessToken))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validatePasswordResetToken_roundTripsToTheSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.generatePasswordResetToken(userId, "user@example.com");
        UUID resolved = jwtProvider.validatePasswordResetToken(token);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void validatePasswordResetToken_rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("purpose", "PASSWORD_RESET")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1800)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtProvider.validatePasswordResetToken(expired))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    /**
     * Clock skew tolerance (clockSkewSeconds(30)): a token that expired only
     * moments ago (within the tolerance window) must still validate - this
     * is what protects legitimate requests from failing due to minor clock
     * drift between servers, without opening a large replay window.
     */
    @Test
    void validateAndGetUserId_toleratesExpirationWithinClockSkewWindow() {
        UUID userId = UUID.randomUUID();
        String justExpired = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().minusSeconds(10))) // 10s past expiry, within the 30s skew tolerance
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        UUID resolved = jwtProvider.validateAndGetUserId(justExpired);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void validateAndGetUserId_rejectsExpirationBeyondClockSkewWindow() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60))) // 60s past expiry, beyond the 30s skew tolerance
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(expired))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
