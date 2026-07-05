package com.saanjha.modules.project.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A free-form discovery tag (e.g. "hackathon", "fintech", "react").
 * Distinct from {@link ProjectRequirement}: tags describe the project itself,
 * requirements describe the people the Lead is looking for.
 */
@Entity
@Table(name = "prj_tags", schema = "prj", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "tag_name"})
})
@Getter
@Setter
public class ProjectTag extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "tag_name", nullable = false, length = 50)
    private String tagName;
}
