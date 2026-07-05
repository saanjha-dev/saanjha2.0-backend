package com.saanjha.modules.team.entity;

/**
 * The Team lifecycle. Deliberately distinct in meaning from each other,
 * not just a literal reading of the brief's arrow chain (CREATED -> ACTIVE ->
 * LOCKED -> ARCHIVED -> DISSOLVED read as a strict single path, which isn't
 * quite right — see {@link com.saanjha.modules.team.service.TeamStatusTransitionValidator}
 * for the actual graph):
 *
 * <ul>
 *   <li>{@code CREATED} — the roster is forming. Seeded the moment the owning
 *       project publishes (RECRUITING); only the founding Lead is a member.
 *       New members can join via accepted Applications/Invitations.</li>
 *   <li>{@code ACTIVE} — the owning project is IN_PROGRESS. Normal operating
 *       state; roster changes still happen (leave, remove, transfer, and
 *       invitation-driven joins — Application-driven joins are paused because
 *       Project itself pauses applications once IN_PROGRESS).</li>
 *   <li>{@code LOCKED} — an explicit, reversible freeze (Lead- or Admin-
 *       triggered), e.g. during a dispute or moderation review. No roster
 *       mutations are permitted while locked. Distinct from ARCHIVED in that
 *       it is meant to be temporary and can transition back to ACTIVE.</li>
 *   <li>{@code ARCHIVED} — the owning project reached COMPLETED or ARCHIVED.
 *       Terminal; the roster is preserved as a permanent historical record,
 *       read-only.</li>
 *   <li>{@code DISSOLVED} — a rare, Admin-only terminal state distinct from
 *       ARCHIVED: the team is being administratively wiped (e.g. a serious
 *       conduct violation) while the project itself may continue and later
 *       recruit a fresh team, as opposed to ARCHIVED's "this team finished
 *       its project normally." History is preserved either way — DISSOLVED
 *       changes what happens next, not whether the past is remembered.</li>
 * </ul>
 */
public enum TeamStatus {
    CREATED,
    ACTIVE,
    LOCKED,
    ARCHIVED,
    DISSOLVED
}
