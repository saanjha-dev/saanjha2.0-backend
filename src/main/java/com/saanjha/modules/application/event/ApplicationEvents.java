package com.saanjha.modules.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Application module for the {@link
 * com.saanjha.modules.application.entity.ProjectApplication} lifecycle.
 * Only business-meaningful transitions are represented — e.g. an internal
 * note being added is not an event, but a decision being made is.
 */
public final class ApplicationEvents {

    private ApplicationEvents() {
    }

    /** Consumers: Notification (alerts the project Lead). Mirrors the master spec's ApplicationSubmitted. */
    public record ApplicationSubmittedEvent(
            UUID applicationId, UUID projectId, UUID applicantId, Instant occurredAt
    ) {}

    /** Consumers: Notification (informs the Lead the applicant pulled out). */
    public record ApplicationWithdrawnEvent(
            UUID applicationId, UUID projectId, UUID applicantId, Instant occurredAt
    ) {}

    /** Consumers: Notification (a soft positive signal to the applicant). */
    public record ApplicationShortlistedEvent(
            UUID applicationId, UUID projectId, UUID applicantId, Instant occurredAt
    ) {}

    /**
     * The pivotal event of the whole module. Consumers (per master spec Section G,
     * "ApplicationAccepted -> Team (Creates Member), Notification"): Team creates
     * the membership; Notification informs the applicant. Application module
     * NEVER creates the membership itself — that would leak a Team responsibility.
     */
    public record ApplicationAcceptedEvent(
            UUID applicationId, UUID projectId, UUID applicantId, UUID reviewedBy, Instant occurredAt
    ) {}

    /** Consumers: Notification (informs the applicant, carries the public-facing reason if any). */
    public record ApplicationRejectedEvent(
            UUID applicationId, UUID projectId, UUID applicantId, UUID reviewedBy, String reason, Instant occurredAt
    ) {}

    /** Fired by the expiration sweep. Consumers: Notification (lets the applicant know it timed out). */
    public record ApplicationExpiredEvent(
            UUID applicationId, UUID projectId, UUID applicantId, Instant occurredAt
    ) {}

    /** Consumers: Notification (a previously rejected applicant is back in play). */
    public record ApplicationReopenedEvent(
            UUID applicationId, UUID projectId, UUID applicantId, UUID reopenedBy, Instant occurredAt
    ) {}
}
