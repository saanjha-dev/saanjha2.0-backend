package com.saanjha.modules.project.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The core aggregate root of the Project module. Per Product Principle 0.4.1,
 * this is the center of gravity of the entire platform: users orbit projects,
 * not the other way around.
 *
 * Ownership is expressed via {@code leadUserId}, a logical (non-FK) reference
 * to auth.auth_users, consistent with the "no cross-schema joins" boundary rule.
 *
 * ARCHIVED is intentionally used as the soft-delete terminal state; there is
 * no separate is_deleted flag, since a second parallel deletion axis would
 * fork the state machine into inconsistent, harder-to-reason-about states.
 */
@Entity
@Table(name = "prj_projects", schema = "prj")
@Getter
@Setter
public class Project extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lead_user_id", nullable = false)
    private UUID leadUserId;

    @Column(nullable = false, length = 150)
    private String title;

    /** Immutable vanity slug, assigned once at creation time (title-derived + random suffix). */
    @Column(nullable = false, length = 180, updatable = false)
    private String slug;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(nullable = false, length = 30)
    private String category; // WEB, MOBILE, AI_ML, BACKEND, DEVOPS, HACKATHON, OPEN_SOURCE, OTHER

    @Column(nullable = false, length = 20)
    private String visibility = "PUBLIC"; // PUBLIC, INVITE_ONLY

    @Column(name = "max_team_size", nullable = false)
    private int maxTeamSize;

    /**
     * Denormalized cache of active roster size, lead included (starts at 1).
     * Owned authoritatively by this module for now; once the Team module ships,
     * it becomes the source of truth and this field is kept in sync via
     * MemberJoined / MemberRemoved event consumption (see Future Extension Points).
     */
    @Column(name = "current_team_size", nullable = false)
    private int currentTeamSize = 1;

    @Column(name = "recruiting_started_at")
    private Instant recruitingStartedAt;

    @Column(name = "team_locked_at")
    private Instant teamLockedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_reason", length = 255)
    private String archivedReason;

    /** Optimistic locking guard against concurrent scope edits / status races. */
    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectRequirement> requirements = new HashSet<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectTag> tags = new HashSet<>();

    // --- BIDIRECTIONAL HELPER METHODS ---

    public void addRequirement(ProjectRequirement requirement) {
        requirements.add(requirement);
        requirement.setProject(this);
    }

    public void removeRequirement(ProjectRequirement requirement) {
        requirements.remove(requirement);
        requirement.setProject(null);
    }

    public void addTag(ProjectTag tag) {
        tags.add(tag);
        tag.setProject(this);
    }

    public void removeTag(ProjectTag tag) {
        tags.remove(tag);
        tag.setProject(null);
    }

    public boolean isMutable() {
        return status != ProjectStatus.COMPLETED && status != ProjectStatus.ARCHIVED;
    }
}
