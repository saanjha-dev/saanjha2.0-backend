package com.saanjha.modules.application.dto;

import java.time.Instant;
import java.util.UUID;

public class InvitationResponseDTOs {

    public record InvitationResponse(
            UUID id,
            UUID projectId,
            UUID invitedUserId,
            UUID invitedBy,
            String preferredRole,
            String message,
            String status,
            Instant respondedAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
