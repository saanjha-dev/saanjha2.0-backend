package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The Membership lifecycle for a single roster seat, as pure stateless logic.
 *
 * <pre>
 * ACTIVE    -> LEFT, REMOVED, SUSPENDED, ARCHIVED
 * SUSPENDED -> ACTIVE (reinstated), REMOVED (escalated), ARCHIVED
 * LEFT      -> (terminal)
 * REMOVED   -> (terminal)
 * ARCHIVED  -> (terminal)
 * </pre>
 *
 * ARCHIVED is reachable only from ACTIVE/SUSPENDED, and only via the
 * team-wide cascade when the owning Team itself archives/dissolves — a row
 * that already reached LEFT or REMOVED during normal team life is already a
 * settled historical fact and is left alone.
 */
public final class MembershipStatusTransitionValidator {

    private static final Map<MembershipStatus, Set<MembershipStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(MembershipStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(MembershipStatus.ACTIVE, EnumSet.of(
                MembershipStatus.LEFT, MembershipStatus.REMOVED, MembershipStatus.SUSPENDED, MembershipStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(MembershipStatus.SUSPENDED, EnumSet.of(
                MembershipStatus.ACTIVE, MembershipStatus.REMOVED, MembershipStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(MembershipStatus.LEFT, EnumSet.noneOf(MembershipStatus.class));
        ALLOWED_TRANSITIONS.put(MembershipStatus.REMOVED, EnumSet.noneOf(MembershipStatus.class));
        ALLOWED_TRANSITIONS.put(MembershipStatus.ARCHIVED, EnumSet.noneOf(MembershipStatus.class));
    }

    private MembershipStatusTransitionValidator() {
    }

    public static boolean isLegal(MembershipStatus from, MembershipStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(MembershipStatus.class)).contains(to);
    }

    public static void assertLegal(MembershipStatus from, MembershipStatus to) {
        if (from == to) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "Membership is already " + to + ".");
        }
        if (!isLegal(from, to)) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Cannot transition membership from " + from + " to " + to + ".");
        }
    }
}
