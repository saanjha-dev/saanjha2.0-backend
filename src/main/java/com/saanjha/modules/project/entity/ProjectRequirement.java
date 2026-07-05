package com.saanjha.modules.project.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A single structural requirement: a skill (and level) the Lead is recruiting
 * for, plus how many open slots exist for it. This is the substrate the
 * (future) Discovery module matches candidate skills against.
 */
@Entity
@Table(name = "prj_requirements", schema = "prj", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "skill_name"})
})
@Getter
@Setter
public class ProjectRequirement extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "skill_level", nullable = false, length = 20)
    private String skillLevel; // BEGINNER, INTERMEDIATE, ADVANCED

    @Column(name = "slots_available", nullable = false)
    private int slotsAvailable;
}
