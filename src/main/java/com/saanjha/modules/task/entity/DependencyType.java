package com.saanjha.modules.task.entity;

/**
 * BLOCKS/BLOCKED_BY are inverses of the same relationship, stored as two
 * directional rows for symmetric querying (see TaskService for how creating
 * one side auto-creates the other). Cycle prevention applies to BLOCKS and
 * PARENT/CHILD only — DUPLICATE_OF and RELATES_TO are non-hierarchical and
 * cannot create a planning deadlock, so they're unrestricted.
 */
public enum DependencyType {
    BLOCKS,
    BLOCKED_BY,
    DUPLICATE_OF,
    RELATES_TO,
    PARENT,
    CHILD
}
