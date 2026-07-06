package com.saanjha.modules.application.service;

import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralizes the Application state machine as pure, stateless logic —
 * mirrors {@code ProjectStatusTransitionValidator}'s design so both modules'
 * lifecycles are equally easy to reason about and unit test in isolation.
 *
 * <pre>
 * SUBMITTED    -> UNDER_REVIEW, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED
 * UNDER_REVIEW -> SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED
 * SHORTLISTED  -> ACCEPTED, REJECTED, WITHDRAWN, EXPIRED
 * ACCEPTED     -> UNDER_REVIEW   (system-only "seat lost" exception, see below)
 * REJECTED     -> UNDER_REVIEW   (the one documented, user-facing "Reopen" exception)
 * WITHDRAWN    -> (terminal)
 * EXPIRED      -> (terminal)
 * </pre>
 *
 * This validator only answers "is X -> Y a legal state change at all". It
 * intentionally does NOT know who is allowed to trigger which transition —
 * e.g. only the applicant may drive a transition to WITHDRAWN, only the
 * reviewing Lead/Admin may drive one to SHORTLISTED/ACCEPTED/REJECTED, and
 * only the scheduled sweep drives one to EXPIRED. That actor-level policy
 * lives in {@code ApplicationService}, which is a separate concern from
 * state-machine legality.
 *
 * FIX (TD19, architecture-review.md §9.2): ACCEPTED -> UNDER_REVIEW was added
 * as a second exception, distinct from the REJECTED -> UNDER_REVIEW "Reopen"
 * a Lead can trigger via the API. This one is SYSTEM-ONLY, triggered
 * exclusively by {@code ApplicationService.reopenAfterSeatLost}, itself
 * invoked only by the listener reacting to Team's
 * {@code MembershipCreationRejectedEvent} — never exposed as a callable
 * endpoint. Before this fix, ACCEPTED was fully terminal, so a "lost the
 * last-slot race" applicant had no legal path back to reconsideration at
 * all; their {@code ProjectApplication} row stayed ACCEPTED forever with no
 * seat — a silent, permanent data-integrity gap.
 */
public final class ApplicationStatusTransitionValidator {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SUBMITTED, EnumSet.of(
                ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED, ApplicationStatus.ACCEPTED,
                ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.UNDER_REVIEW, EnumSet.of(
                ApplicationStatus.SHORTLISTED, ApplicationStatus.ACCEPTED,
                ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SHORTLISTED, EnumSet.of(
                ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.ACCEPTED, EnumSet.of(ApplicationStatus.UNDER_REVIEW));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.REJECTED, EnumSet.of(ApplicationStatus.UNDER_REVIEW));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.EXPIRED, EnumSet.noneOf(ApplicationStatus.class));
    }

    private ApplicationStatusTransitionValidator() {
    }

    public static boolean isLegal(ApplicationStatus from, ApplicationStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ApplicationStatus.class)).contains(to);
    }

    public static void assertLegal(ApplicationStatus from, ApplicationStatus to) {
        if (from == to) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "Application is already " + to + ".");
        }
        if (!isLegal(from, to)) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Cannot transition application from " + from + " to " + to + ".");
        }
    }
}
