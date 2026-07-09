package com.saanjha.modules.contribution.entity;

/**
 * The contribution ACTION taxonomy — distinct from a Task's own domain type
 * (FEATURE/BUG/CHORE/...), which is carried on the ledger entry only as
 * display context ({@code contextTaskType}), never as a second governing
 * enum. A "bug fix" and a "feature" are both a {@code TASK_COMPLETION};
 * what kind of task it was is metadata about that completion, not a
 * different category of contribution.
 *
 * {@code TASK_ABANDONED} is this module's own addition, not in the brief's
 * literal list — added because the brief explicitly asks "Who abandoned
 * work?" as a question Contribution must answer. It always scores zero
 * (see the V16 seed) but still feeds the Reliability reputation dimension.
 */
public enum ContributionType {
    TASK_COMPLETION,
    TASK_REVIEW,
    LEADERSHIP,
    MENTORSHIP,
    PLANNING,
    TASK_ABANDONED
}
