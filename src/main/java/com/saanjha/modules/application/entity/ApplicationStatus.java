package com.saanjha.modules.application.entity;

/**
 * The Application lifecycle (reconciled from two sources: the master spec's
 * Section F.2 — PENDING/REVIEWING/ACCEPTED/REJECTED/WITHDRAWN — extended per
 * this module's brief with SHORTLISTED and EXPIRED).
 *
 * DRAFT and CANCELLED, both requested in the brief's superset state list,
 * were deliberately dropped for this aggregate:
 *  - DRAFT would imply a save-before-submit flow. No such endpoint exists
 *    (POST creates a fully SUBMITTED application, per the master spec's
 *    "Submit an inbound join request" endpoint semantics) — an unused state
 *    is worse than no state.
 *  - CANCELLED has no distinct meaning here once WITHDRAWN (applicant-side)
 *    and REJECTED (owner-side) already exist; forcing it in would just be a
 *    second name for one of those. CANCELLED is used instead on the sibling
 *    {@link com.saanjha.modules.application.entity.InvitationStatus} state
 *    machine as REVOKED, where it has a real, distinct meaning (the Lead
 *    pulling back an invitation the invitee hasn't responded to yet).
 *
 * SUBMITTED, UNDER_REVIEW, SHORTLISTED are "open"; ACCEPTED, REJECTED,
 * WITHDRAWN, EXPIRED are terminal — with one documented exception: a Lead
 * may REOPEN a REJECTED application back to UNDER_REVIEW (Spec: "Reopen" API,
 * ApplicationReopenedEvent).
 */
public enum ApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    SHORTLISTED,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED
}
