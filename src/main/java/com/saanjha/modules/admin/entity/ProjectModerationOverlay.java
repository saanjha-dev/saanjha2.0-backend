package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Governance-only overlay for a project: {@code locked}/{@code hidden}/
 * {@code featured} are presentation and access-control flags that sit
 * *alongside* Project's own {@code ProjectStatus} state machine, not inside
 * it. Deliberately kept out of the {@code prj} schema and out of Project's
 * own aggregate: Project's state machine (DRAFT/RECRUITING/IN_PROGRESS/
 * COMPLETED/ARCHIVED) is a carefully validated, already-tested domain
 * concept (see module-health.md's assessment of the Project module), and
 * "an admin froze this for review" is not a lifecycle transition — it is
 * Admin's own governance fact about a project it doesn't own.
 *
 * Full removal ("Remove Project" in the Admin brief) is handled differently:
 * it reuses Project's own {@code ARCHIVED} terminal state via
 * {@code ProjectService.transitionStatus}, because that IS a legitimate
 * state-machine transition Project already owns and enforces — see
 * {@code ProjectModerationService} for the split.
 *
 * Enforcement note (Future Extension Point): today, {@code locked=true} is
 * visible to callers of Admin's own read APIs and is the source of truth
 * Admin's dashboard renders, but Project's own mutation endpoints do not yet
 * check it before allowing a Lead to edit. Wiring that check into
 * {@code ProjectController}/{@code ProjectService} is a small, isolated
 * follow-up (a single guard clause) — see the Final Report.
 */
@Entity
@Table(name = "adm_project_overlays", schema = "adm")
@Getter
@Setter
public class ProjectModerationOverlay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "locked_reason", length = 1000)
    private String lockedReason;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "hidden_reason", length = 1000)
    private String hiddenReason;

    @Column(nullable = false)
    private boolean featured = false;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
