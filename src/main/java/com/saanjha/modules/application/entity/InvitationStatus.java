package com.saanjha.modules.application.entity;

/**
 * The Invitation lifecycle. Fully independent of {@link ApplicationStatus}:
 * an accepted invitation never becomes a {@link ProjectApplication} row —
 * it goes straight to an InvitationAcceptedEvent for the Team module,
 * per the brief's explicit instruction that "Invitation should NOT create
 * Team membership [directly]. It should only produce business events."
 *
 * SENT -> ACCEPTED, DECLINED, EXPIRED, REVOKED, SEAT_LOST. All five outcomes are terminal.
 *
 * FIX (TD19, architecture-review.md §9.2): {@code SEAT_LOST} is new — added
 * for the same last-slot-race compensating flow as Application's
 * ACCEPTED -> UNDER_REVIEW exception, but deliberately NOT the same
 * mechanism. Application has a review queue to reopen an application back
 * into; Invitation has no equivalent concept — there is no "queue" a Lead
 * reviews invitations from, they were sent to one specific person on
 * purpose. Forcing Invitation's ACCEPTED status back to SENT would be
 * misleading (the invitee already said yes; re-sending implies they didn't).
 * A distinct terminal state that honestly records "they accepted, but the
 * seat was gone by the time it was processed" is the more truthful model,
 * and gives the Lead a clear, distinguishable outcome to act on (typically:
 * send a fresh invitation to someone else, or apologize to this one).
 */
public enum InvitationStatus {
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    REVOKED,
    SEAT_LOST
}
