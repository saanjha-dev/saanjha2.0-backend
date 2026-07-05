package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.TeamStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The Team lifecycle, as pure stateless logic — same design as
 * ProjectStatusTransitionValidator/ApplicationStatusTransitionValidator.
 *
 * <pre>
 * CREATED  -> ACTIVE, ARCHIVED, DISSOLVED
 * ACTIVE   -> LOCKED, ARCHIVED, DISSOLVED
 * LOCKED   -> ACTIVE, ARCHIVED, DISSOLVED   (unlock is the one reversible edge)
 * ARCHIVED -> (terminal)
 * DISSOLVED -> (terminal)
 * </pre>
 */
public final class TeamStatusTransitionValidator {

    private static final Map<TeamStatus, Set<TeamStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TeamStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TeamStatus.CREATED, EnumSet.of(TeamStatus.ACTIVE, TeamStatus.ARCHIVED, TeamStatus.DISSOLVED));
        ALLOWED_TRANSITIONS.put(TeamStatus.ACTIVE, EnumSet.of(TeamStatus.LOCKED, TeamStatus.ARCHIVED, TeamStatus.DISSOLVED));
        ALLOWED_TRANSITIONS.put(TeamStatus.LOCKED, EnumSet.of(TeamStatus.ACTIVE, TeamStatus.ARCHIVED, TeamStatus.DISSOLVED));
        ALLOWED_TRANSITIONS.put(TeamStatus.ARCHIVED, EnumSet.noneOf(TeamStatus.class));
        ALLOWED_TRANSITIONS.put(TeamStatus.DISSOLVED, EnumSet.noneOf(TeamStatus.class));
    }

    private TeamStatusTransitionValidator() {
    }

    public static boolean isLegal(TeamStatus from, TeamStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TeamStatus.class)).contains(to);
    }

    public static void assertLegal(TeamStatus from, TeamStatus to) {
        if (from == to) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "Team is already " + to + ".");
        }
        if (!isLegal(from, to)) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Cannot transition team from " + from + " to " + to + ".");
        }
    }
}
