package com.saanjha.modules.application.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal, reviewer-only note attached to an application — never shown
 * to the applicant (distinct from {@code decisionReason} on
 * {@link ProjectApplication}, which IS applicant-facing). Append-only:
 * notes are a timeline, not an editable field, so there is no update path.
 */
@Entity
@Table(name = "app_notes", schema = "app")
public class ApplicationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApplicationNote() {
        // JPA
    }

    public ApplicationNote(UUID applicationId, UUID authorId, String note) {
        this.applicationId = applicationId;
        this.authorId = authorId;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
