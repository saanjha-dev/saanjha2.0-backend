package com.saanjha.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class ResponseDTOs {
    private ResponseDTOs() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthTokens(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        public AuthTokens(String accessToken, String refreshToken, long expiresIn) {
            this(accessToken, refreshToken, "Bearer", expiresIn);
        }


    }

    public record PasswordResetTokenResponse(
            String resetToken,
            long expiresIn
    ) {}
}