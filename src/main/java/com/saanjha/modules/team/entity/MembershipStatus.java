package com.saanjha.modules.team.entity;

/**
 * The Membership lifecycle for a single roster seat.
 *
 * The brief's literal chain (ACTIVE -> LEFT -> REMOVED -> SUSPENDED -> ARCHIVED)
 * reads as one linear path, which doesn't hold up: there's no reason a
 * REMOVED row would ever need to become SUSPENDED. The actual legal graph
 * (see {@link com.saanjha.modules.team.service.MembershipStatusTransitionValidator}):
 *
 * <ul>
 *   <li>{@code ACTIVE} — currently on the roster and able to act.</li>
 *   <li>{@code LEFT} — departed voluntarily. Terminal for this row.</li>
 *   <li>{@code REMOVED} — removed by the Lead/Admin. Terminal for this row.</li>
 *   <li>{@code SUSPENDED} — temporarily paused (Lead/Admin action, e.g. a
 *       pending dispute). Still occupies a roster seat (counts against
 *       capacity) but cannot act. Reversible: SUSPENDED -> ACTIVE
 *       (reinstated) or SUSPENDED -> REMOVED (escalated).</li>
 *   <li>{@code ARCHIVED} — the owning Team itself moved to ARCHIVED/DISSOLVED;
 *       every remaining live row is archived en masse as a terminal cascade,
 *       distinct from an individual LEFT/REMOVED which happens mid-team-life.</li>
 * </ul>
 *
 * "Rejoining" a team after LEFT/REMOVED deliberately does NOT resurrect the
 * old row (see the Membership entity's Javadoc) — a new row is created with
 * {@code joinedVia = REJOINED}, so the original row remains an accurate,
 * untouched historical record of that specific stint.
 */
public enum MembershipStatus {
    ACTIVE,
    LEFT,
    REMOVED,
    SUSPENDED,
    ARCHIVED
}
