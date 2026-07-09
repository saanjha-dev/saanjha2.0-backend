package com.saanjha.modules.contribution.entity;

/**
 * A ledger entry is NEVER silently dropped for looking suspicious — it's
 * always recorded (auditability/trust requires the full picture), but
 * flagged and scored at a reduced or zero multiplier pending review. This
 * enum is the vocabulary for "why this entry looks suspicious," not a
 * rejection mechanism.
 */
public enum IntegrityFlag {
    NONE,
    /** Task moved IN_PROGRESS -> DONE implausibly fast — see ContributionScoringEngine's threshold. */
    SUSPICIOUS_VELOCITY,
    /** The reviewer (TaskCompletedEvent.reviewedBy) is the same person as the assignee. */
    SELF_REVIEW,
    /** A task was reassigned an unusual number of times before completion — possible review/credit farming. */
    REASSIGNMENT_CHURN,
    /** A task was reopened an unusual number of times — possible rework-count inflation. */
    REOPEN_FARMING
}
