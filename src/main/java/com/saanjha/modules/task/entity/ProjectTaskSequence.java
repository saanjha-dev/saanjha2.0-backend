package com.saanjha.modules.task.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Tracks the current sequence number for task identifiers per project.
 * Located in the task schema to avoid cross-schema mutations.
 */
@Entity
@Table(name = "tsk_project_sequences", schema = "tsk")
@Getter
@Setter
public class ProjectTaskSequence {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "task_prefix", nullable = false, length = 10)
    private String taskPrefix;

    @Column(name = "current_sequence", nullable = false)
    private int currentSequence = 0;
}
