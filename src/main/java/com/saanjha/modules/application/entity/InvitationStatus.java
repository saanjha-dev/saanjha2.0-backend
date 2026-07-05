package com.saanjha.modules.application.entity;

/**
 * The Invitation lifecycle. Fully independent of {@link ApplicationStatus}:
 * an accepted invitation never becomes a {@link ProjectApplication} row —
 * it goes straight to an InvitationAcceptedEvent for the Team module,
 * per the brief's explicit instruction that "Invitation should NOT create
 * Team membership [directly]. It should only produce business events."
 *
 * SENT -> ACCEPTED, DECLINED, EXPIRED, REVOKED. All four outcomes are terminal.
 */
public enum InvitationStatus {
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    REVOKED
}
