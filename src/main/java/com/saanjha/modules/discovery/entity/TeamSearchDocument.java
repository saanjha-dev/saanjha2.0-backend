package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight read model of a team, projected from {@code TeamEvents}.
 * Required-skill and "still recruiting" matching for team discovery is
 * served by joining to {@link ProjectSearchDocument} on {@code projectId} at
 * query time — both tables live in this module's own {@code dsc} schema, so
 * this is not a cross-schema join.
 */
@Entity
@Table(name = "dsc_team_documents", schema = "dsc")
@Getter
@Setter
public class TeamSearchDocument {

    @Id
    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "founder_user_id", nullable = false)
    private UUID founderUserId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "current_size", nullable = false)
    private int currentSize = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
