package com.saanjha.modules.task.entity;

/**
 * The user-facing activity feed vocabulary. Deliberately does NOT include
 * "comment added" — Task doesn't own comments (that's Chat's or a future
 * dedicated module's responsibility); adding it here would be exactly the
 * kind of responsibility leak the module's brief explicitly warns against.
 */
public enum TaskActivityType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    CHECKLIST_ITEM_ADDED,
    CHECKLIST_ITEM_COMPLETED,
    DEPENDENCY_ADDED,
    DEPENDENCY_REMOVED,
    BLOCKED,
    UNBLOCKED,
    ARCHIVED,
    RESTORED
}
