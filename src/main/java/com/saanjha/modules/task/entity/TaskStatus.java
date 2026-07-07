package com.saanjha.modules.task.entity;

/**
 * The Task lifecycle. Reconciled from the brief's superset list the same way
 * every prior module's state machine was: the literal chain
 * (BACKLOG->TODO->IN_PROGRESS->IN_REVIEW->DONE->ARCHIVED) is the "happy path"
 * only. BLOCKED, CANCELLED, and DUPLICATE are real states with their own
 * edges; "Reopened" and "Deferred" are NOT separate states — they're
 * transitions back to an existing state (DONE/CANCELLED -> TODO for reopen;
 * TODO/IN_PROGRESS -> BACKLOG for defer) with no distinct status of their own.
 *
 * See {@link com.saanjha.modules.task.service.TaskStatusTransitionValidator}
 * for the full transition graph. One rule worth calling out here: "blocked
 * tasks cannot move to DONE" is not a separately-coded runtime check — it's
 * structurally impossible in the graph itself, since BLOCKED only transitions
 * to IN_PROGRESS or CANCELLED, never directly to DONE.
 */
public enum TaskStatus {
    BACKLOG,
    TODO,
    IN_PROGRESS,
    BLOCKED,
    IN_REVIEW,
    DONE,
    CANCELLED,
    DUPLICATE,
    ARCHIVED
}
