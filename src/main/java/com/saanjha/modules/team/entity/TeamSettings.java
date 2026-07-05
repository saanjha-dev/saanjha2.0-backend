package com.saanjha.modules.team.entity;

/**
 * Team's own configuration — deliberately scoped to things Team itself
 * enforces or exposes, not a dumping ground for every "policy"-sounding word.
 *
 * Two settings requested in the brief were rejected during design, not
 * silently dropped:
 * <ul>
 *   <li><b>Maximum Members</b> — redundant with {@code Project.maxTeamSize},
 *       which Team already treats as authoritative (re-checked live via
 *       {@code ProjectService.getSnapshot()} on every {@code addMember}).
 *       A second, independently-editable cap here would create exactly the
 *       split-brain-authority problem the architecture spec called out for
 *       leadership. There is one team-size ceiling, and Project owns it.</li>
 *   <li><b>Join Policy / Auto-Accept</b> — these describe how *Application*
 *       decides to accept someone, which is Application's decision logic,
 *       not Team's. Team has no "front door" of its own; every arrival is
 *       already a decided {@code ApplicationAcceptedEvent} or
 *       {@code InvitationAcceptedEvent} by the time Team sees it.</li>
 * </ul>
 *
 * {@code memberInvitationPolicy} is included as a genuine Team-owned policy
 * for the future, but is NOT yet enforced anywhere: {@code InvitationService
 * .sendInvitation} is currently hardcoded to Lead-only authorization. Wiring
 * that check to consult this setting is an explicit, documented extension
 * point (see the module's final write-up), not a silent gap.
 *
 * Stored as JSONB rather than typed columns specifically so future settings
 * can be added without a migration — this record is the typed read/write
 * contract on top of that loosely-structured storage.
 */
public record TeamSettings(
        RosterVisibility visibility,
        boolean guestAccessEnabled,
        ActivityVisibility activityVisibility,
        MemberInvitationPolicy memberInvitationPolicy
) {
    public static TeamSettings defaults() {
        return new TeamSettings(RosterVisibility.PUBLIC, false, ActivityVisibility.MEMBERS_ONLY, MemberInvitationPolicy.LEAD_ONLY);
    }

    public enum RosterVisibility {
        PUBLIC, MEMBERS_ONLY, PRIVATE
    }

    public enum ActivityVisibility {
        PUBLIC, MEMBERS_ONLY, LEAD_ONLY
    }

    public enum MemberInvitationPolicy {
        LEAD_ONLY, ANY_MEMBER
    }
}
