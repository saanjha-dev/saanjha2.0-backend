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
     * NOTE (Notification module build): {@code leadUserId} added - sourced from
     * {@code Invitation.invitedBy}, which was already being populated at send time
     * and requires no new lookup.
     */
    public record InvitationAcceptedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID leadUserId, Instant occurredAt
    ) {}

    /** Consumers: Notification (informs the Lead so they can invite someone else).
     * NOTE (Notification module build): {@code leadUserId} added, same reasoning as {@link InvitationAcceptedEvent}. */
    public record InvitationDeclinedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID leadUserId, String reason, Instant occurredAt
    ) {}

    /** Fired by the expiration sweep. Consumers: Notification. */
    public record InvitationExpiredEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, Instant occurredAt
    ) {}

    /** Consumers: Notification (lets the invitee know the offer was pulled). */
    public record InvitationRevokedEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID revokedBy, String reason, Instant occurredAt
    ) {}

    /**
     * FIX (TD19, architecture-review.md §9.2): the compensating outcome for
     * an accepted invitation that lost a last-slot capacity race in Team.
     * Consumers: Notification (tell the invitee honestly what happened,
     * distinct from a plain decline/expiry), the project Lead (prompt to
     * invite someone else for the now-still-open slot).
     */
    public record InvitationSeatLostEvent(
            UUID invitationId, UUID projectId, UUID invitedUserId, UUID leadUserId, String reason, Instant occurredAt
    ) {}
}
