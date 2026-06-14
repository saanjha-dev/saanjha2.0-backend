package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.config.RsaKeyProperties;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    private final RsaKeyProperties rsaKeys;
    private static final String PASSWORD_RESET_PURPOSE = "PASSWORD_RESET";

    @lombok.Getter
    @Value("${app.jwt.expiration-ms:900000}") // Default 15 mins
    private long jwtExpirationMs;

    /**
     * Generates an RS256 signed JWT for the authenticated user.
     */
    // Notice we removed the "String role" parameter entirely
    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant validity = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("type", "access") // Pure Identity, Zero Authorization Bloat
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(rsaKeys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Validates the token signature and expiration, returning the User ID (Subject).
     */
    public UUID validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(rsaKeys.publicKey())
                    .clockSkewSeconds(30) // Mitigate minor server time drift
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type"))) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid token type.");
            }

            return UUID.fromString(claims.getSubject());

        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            throw new AppException(ErrorCode.UNAUTHORIZED, "Access token has expired. Please refresh.");
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT presented: {}", ex.getMessage());
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid access token.");
        }
    }
    public String generatePasswordResetToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("purpose", PASSWORD_RESET_PURPOSE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (5 * 60 * 1000))) // 5 minutes
                // FIXED: Use the injected RSA Private Key just like the access token
                .signWith(rsaKeys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public UUID validatePasswordResetToken(String token) {
        try {
            // FIXED: Use the JJWT 0.12.x parser with the RSA Public Key
            Claims claims = Jwts.parser()
                    .verifyWith(rsaKeys.publicKey())
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String purpose = claims.get("purpose", String.class);

            if (!PASSWORD_RESET_PURPOSE.equals(purpose)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid reset token purpose.");
            }

            return UUID.fromString(claims.getSubject());

        } catch (ExpiredJwtException ex) {
            log.debug("Password reset JWT expired: {}", ex.getMessage());
            throw new AppException(ErrorCode.UNAUTHORIZED, "Password reset link has expired.");
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid password reset JWT presented: {}", ex.getMessage());
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid password reset link.");
        }
    }
}