package com.saanjha.modules.admin.entity;

/**
 * OPEN -> IN_REVIEW -> (RESOLVED | DISMISSED). ESCALATED is a side channel
 * from IN_REVIEW for reports a moderator can't decide alone (Trust & Safety
 * escalation), not a terminal state — it always resolves back through
 * RESOLVED or DISMISSED so the queue metrics have exactly two closure states
 * to reason about.
 */
public enum ReportStatus {
    OPEN,
    IN_REVIEW,
    ESCALATED,
    RESOLVED,
    DISMISSED
}
