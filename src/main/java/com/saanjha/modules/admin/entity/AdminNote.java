package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only internal moderator context on a target — never shown to the
 * target user. Distinct from {@link ModerationAction} (a decision with
 * consequences) and from {@link Report} (a complaint): a note is neither —
 * it's institutional memory ("this Lead has been warned informally twice
 * before", "possible sockpuppet of report #4471") that future moderators
 * reviewing the same target benefit from seeing.
 */
@Entity
@Table(name = "adm_notes", schema = "adm")
@Getter
@Setter
public class AdminNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
