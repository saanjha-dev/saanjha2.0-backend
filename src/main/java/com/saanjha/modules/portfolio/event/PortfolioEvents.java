package com.saanjha.modules.portfolio.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Portfolio module. Portfolio is primarily
 * an event CONSUMER (see {@code PortfolioEventListener}) — it only publishes
 * when it has performed genuine business work of its own (a new verified
 * entry exists, a badge was earned, a visibility choice changed), never as a
 * mirror of something it merely observed.
 */
public final class PortfolioEvents {

    private PortfolioEvents() {
    }

    /**
     * Fired once, the moment one user's verified work on one project is
     * finalized into an immutable {@code PortfolioEntry}. Deliberately
     * per (user, project) rather than an aggregate "whole team is done"
     * signal — Portfolio does not own the project's roster size, so it has
     * no reliable way to know when "everyone" has been processed.
     * Consumers: Notification (future — "your portfolio grew").
     */
    public record PortfolioEntryCreatedEvent(
            UUID entryId, UUID userId, UUID projectId, double contributionScore,
            boolean wasLead, Instant generatedAt
    ) {}

    /**
     * A softer public-facing signal than {@code PortfolioEntryCreatedEvent}:
     * fired at the same moment, intended for consumers that care about "a
     * portfolio was (re)generated" without needing the scoring detail
     * (e.g. a future Discovery ranking refresh, or a cache invalidation
     * hook). Kept as a separate, thinner event rather than overloading
     * {@code PortfolioEntryCreatedEvent} with concerns only some consumers
     * need.
     */
    public record PortfolioGeneratedEvent(UUID userId, UUID projectId, Instant generatedAt) {}

    /** Producer: Portfolio's badge engine. Consumers: Notification (future — "you earned a badge"). */
    public record BadgeAwardedEvent(UUID userId, String badgeType, Instant awardedAt) {}

    /** Consumers: none yet in this codebase; reserved for a future Discovery/recruiter-facing cache. */
    public record PortfolioVisibilityChangedEvent(UUID userId, String visibility, Instant changedAt) {}

    /**
     * Reserved for the future export pipeline (PDF/resume/public web page —
     * see the module write-up's Future Extension Points). No producer
     * exists yet; declared now so the eventual exporter's contract is
     * visible to reviewers up front rather than invented ad hoc later.
     */
    public record PortfolioExportRequestedEvent(UUID userId, String format, Instant requestedAt) {}
}
