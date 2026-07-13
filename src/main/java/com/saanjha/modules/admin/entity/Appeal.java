package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's appeal of a specific {@link ModerationAction} taken against them.
 * Deliberately keyed to the action, not to the user/target directly — this
 * is what lets "Appeal" mean something precise ("I'm contesting THIS
 * decision") rather than a vague complaint thread, and lets
 * {@code ModerationAction.reversed} be flipped atomically when an appeal is
 * granted.
 */
@Entity
@Table(name = "adm_appeals", schema = "adm")
@Getter
@Setter
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "moderation_action_id", nullable = false)
    private UUID moderationActionId;

    @Column(name = "appellant_user_id", nullable = false)
    private UUID appellantUserId;

    @Column(nullable = false, length = 2000)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppealStatus status = AppealStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_notes", length = 2000)
    private String decisionNotes;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
