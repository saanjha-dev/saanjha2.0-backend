package com.saanjha.modules.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Application module for the {@link
 * com.saanjha.modules.application.entity.Invitation} lifecycle.
 */
public final class InvitationEvents {

    private InvitationEvents() {
    }

    /** Consumers: Notification (alerts the invited user). */
    public record InvitationSentEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID invitedBy, Instant occurredAt
    ) {}

    /**
     * Consumers: Team (creates the membership — exactly the same downstream
     * effect as ApplicationAcceptedEvent, just from the other entry point),
     * Notification (confirms to the Lead).
     */
    public record InvitationAcceptedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, Instant occurredAt
    ) {}

    /** Consumers: Notification (informs the Lead so they can invite someone else). */
    public record InvitationDeclinedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, String reason, Instant occurredAt
    ) {}

    /** Fired by the expiration sweep. Consumers: Notification. */
    public record InvitationExpiredEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, Instant occurredAt
    ) {}

    /** Consumers: Notification (lets the invitee know the offer was pulled). */
    public record InvitationRevokedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID revokedBy, String reason, Instant occurredAt
    ) {}
}
