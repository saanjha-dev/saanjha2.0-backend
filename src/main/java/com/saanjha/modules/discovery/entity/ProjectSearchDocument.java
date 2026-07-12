package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Discovery's own read model of a project, built entirely from
 * {@code ProjectEvents.ProjectDiscoveryUpdatedEvent}/{@code ProjectArchivedEvent}.
 * Never read the {@code prj} schema directly — that would violate the module
 * boundary rule this codebase enforces everywhere else.
 *
 * {@code search_vector} is intentionally NOT mapped as a Java field: it is
 * maintained purely by a DB trigger (see V22 migration) and is only ever
 * read via native full-text queries in {@code ProjectSearchRepositoryImpl}.
 * JSONB array columns follow this codebase's established convention
 * (see {@code PortfolioEntry.technologiesJson}) of storing them as a raw JSON
 * string on the entity, serialized/deserialized by the owning service.
 */
@Entity
@Table(name = "dsc_project_documents", schema = "dsc")
@Getter
@Setter
public class ProjectSearchDocument {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "lead_user_id", nullable = false)
    private UUID leadUserId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(name = "description_excerpt", columnDefinition = "TEXT")
    private String descriptionExcerpt;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", nullable = false, columnDefinition = "jsonb")
    private String requiredSkillsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private String tagsJson = "[]";

    @Column(name = "max_team_size", nullable = false)
    private int maxTeamSize;

    @Column(name = "current_team_size", nullable = false)
    private int currentTeamSize;

    @Column(name = "is_indexed", nullable = false)
    private boolean indexed = true;

    @Column(name = "popularity_score", nullable = false)
    private double popularityScore = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

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
