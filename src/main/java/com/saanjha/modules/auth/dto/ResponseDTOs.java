package com.saanjha.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class ResponseDTOs {
    private ResponseDTOs() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthTokens(
            String accessToken,
            String refreshToken,
            String tokenType,
            Long expiresIn,
            Boolean mfaRequired,
            String mfaToken
    ) {
        public AuthTokens(String accessToken, String refreshToken, long expiresIn) {
            this(accessToken, refreshToken, "Bearer", expiresIn, false, null);
        }
        
        public static AuthTokens requireMfa(String mfaToken) {
            return new AuthTokens(null, null, null, null, true, mfaToken);
        }
    }

    public record PasswordResetTokenResponse(
            String resetToken,
            long expiresIn
    ) {}

    public record MfaSetupResponse(
            String secret,
            String qrDataUri
    ) {}

    public record MfaStatusResponse(
            boolean mfaEnabled
    ) {}
}