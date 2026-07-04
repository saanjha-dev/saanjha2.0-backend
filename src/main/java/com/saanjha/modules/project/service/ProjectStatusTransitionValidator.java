package com.saanjha.modules.project.service;

import com.saanjha.modules.project.entity.ProjectStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralizes the Project state machine (Spec Section F.1) as pure, stateless
 * logic. Deliberately kept out of ProjectService so the transition rules can
 * be unit tested in complete isolation from Spring, JPA, and Redis.
 *
 * <pre>
 * DRAFT       -> RECRUITING, ARCHIVED
 * RECRUITING  -> IN_PROGRESS, ARCHIVED
 * IN_PROGRESS -> COMPLETED, ARCHIVED
 * COMPLETED   -> (terminal)
 * ARCHIVED    -> (terminal)
 * </pre>
 */
public final class ProjectStatusTransitionValidator {

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ProjectStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ProjectStatus.DRAFT, EnumSet.of(ProjectStatus.RECRUITING, ProjectStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(ProjectStatus.RECRUITING, EnumSet.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(ProjectStatus.IN_PROGRESS, EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(ProjectStatus.COMPLETED, EnumSet.noneOf(ProjectStatus.class));
        ALLOWED_TRANSITIONS.put(ProjectStatus.ARCHIVED, EnumSet.noneOf(ProjectStatus.class));
    }

    private ProjectStatusTransitionValidator() {
    }

    public static boolean isLegal(ProjectStatus from, ProjectStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ProjectStatus.class)).contains(to);
    }

    /**
     * @throws AppException(STATE_TRANSITION_FAILED) if the transition is not permitted.
     */
    public static void assertLegal(ProjectStatus from, ProjectStatus to) {
        if (from == to) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Project is already in status " + to + ".");
        }
        if (!isLegal(from, to)) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Cannot transition project from " + from + " to " + to + ".");
        }
    }
}
