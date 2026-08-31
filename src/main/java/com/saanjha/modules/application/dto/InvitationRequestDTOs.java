package com.saanjha.modules.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class InvitationRequestDTOs {

    public record SendInvitationRequest(
            @jakarta.validation.constraints.NotBlank(message = "User handle is required")
            String userHandle,

            @Size(max = 100)
            String preferredRole,

            @Size(max = 2000, message = "Message cannot exceed 2000 characters")
            String message
    ) {}

    public record DeclineInvitationRequest(
            @Size(max = 500)
            String reason
    ) {}

    public record RevokeInvitationRequest(
            @Size(max = 500)
            String reason
    ) {}
}
