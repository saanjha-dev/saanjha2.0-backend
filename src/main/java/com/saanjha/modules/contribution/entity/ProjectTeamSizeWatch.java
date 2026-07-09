package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal-only derived state powering the "small team = higher difficulty
 * multiplier" scoring input, built purely from consuming
 * MemberJoinedEvent/MemberLeftEvent/MemberRemovedEvent — never a read of
 * Team's schema.
 */
@Entity
@Table(name = "con_project_team_size", schema = "con")
public class ProjectTeamSizeWatch {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "current_size", nullable = false)
    private int currentSize = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProjectTeamSizeWatch() {
    }

    public static ProjectTeamSizeWatch blank(UUID projectId) {
        ProjectTeamSizeWatch watch = new ProjectTeamSizeWatch();
        watch.projectId = projectId;
        return watch;
    }

    public void setSize(int size) {
        this.currentSize = Math.max(size, 0);
        this.updatedAt = Instant.now();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getCurrentSize() {
        return currentSize;
    }
}
