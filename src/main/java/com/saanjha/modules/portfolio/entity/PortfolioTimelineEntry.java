package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Purely additive, append-only — one row per consumed event that's
 * meaningful to show a human. Never updated, never deleted (a project being
 * archived later doesn't erase the fact that the user joined it). Rendering
 * ("Completed project 'X' as Team Lead") is precomputed at write time into
 * {@code description} rather than reconstructed at read time from the
 * other snapshot tables — this row must remain meaningful even if the
 * entry it refers to is, hypothetically, unavailable.
 */
@Entity
@Table(name = "ptf_timeline", schema = "ptf")
public class PortfolioTimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private TimelineEventType eventType;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PortfolioTimelineEntry() {
        // JPA
    }

    public static PortfolioTimelineEntry create(UUID userId, UUID projectId, TimelineEventType eventType,
                                                 String description, Instant occurredAt) {
        PortfolioTimelineEntry entry = new PortfolioTimelineEntry();
        entry.userId = userId;
        entry.projectId = projectId;
        entry.eventType = eventType;
        entry.description = description;
        entry.occurredAt = occurredAt;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public TimelineEventType getEventType() {
        return eventType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
