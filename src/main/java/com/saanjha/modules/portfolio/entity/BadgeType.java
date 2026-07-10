package com.saanjha.modules.portfolio.entity;

/**
 * Every value here must be awardable purely from events this module already
 * consumes — no badge is ever manually assignable (enforced at the API
 * layer: there is no "award badge" endpoint at all, only the automatic
 * engine). See {@code PortfolioBadgeEngine} for the exact trigger per type.
 *
 * Deliberately NOT included, with reasoning (documented gaps, not oversights):
 * - TOP_CONTRIBUTOR: requires a platform-wide ranking, which needs either a
 *   scheduled leaderboard job or a Discovery-side ranking signal — neither
 *   exists today. A badge that silently means nothing (or worse, is awarded
 *   to everyone) is worse than no badge.
 * - EARLY_ADOPTER: requires a "user #N of the platform" concept. User's own
 *   module doesn't expose a signup-order query; fabricating this from
 *   {@code createdAt} comparison would mean Portfolio reaching into User's
 *   schema, which the boundary rule forbids.
 */
public enum BadgeType {
    /** Awarded the first time an entry with {@code wasLead = true} is generated for a user. */
    PROJECT_LEADER,

    /** Awarded the first time a project tagged with an open-source-style tag is completed. */
    OPEN_SOURCE_CONTRIBUTOR,

    /** Tag-heuristic badge — see {@code PortfolioBadgeEngine.BACKEND_TAG_KEYWORDS}. */
    BACKEND_SPECIALIST,

    /** Tag-heuristic badge — see {@code PortfolioBadgeEngine.FRONTEND_TAG_KEYWORDS}. */
    FRONTEND_SPECIALIST,

    /** Mirrors Contribution's own milestone thresholds exactly — never recomputed, only relayed. */
    TASKS_COMPLETED_10,
    TASKS_COMPLETED_25,
    TASKS_COMPLETED_50,
    TASKS_COMPLETED_100,
    TASKS_COMPLETED_250,
    TASKS_COMPLETED_500,
    TASKS_COMPLETED_1000
}
