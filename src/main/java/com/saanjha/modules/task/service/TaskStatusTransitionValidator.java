package com.saanjha.modules.task.service;

import com.saanjha.modules.task.entity.TaskStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The Task state machine, as pure stateless logic — same design as every
 * prior module's validator. See {@link TaskStatus}'s Javadoc for how the
 * brief's superset list was reconciled into this graph.
 *
 * <pre>
 * BACKLOG     -> TODO, CANCELLED, DUPLICATE
 * TODO        -> IN_PROGRESS, BACKLOG (defer), CANCELLED, DUPLICATE
 * IN_PROGRESS -> IN_REVIEW, BLOCKED, TODO (defer/pause), CANCELLED, DUPLICATE
 * BLOCKED     -> IN_PROGRESS (unblocked), CANCELLED, DUPLICATE
 * IN_REVIEW   -> DONE, IN_PROGRESS (changes requested), CANCELLED, DUPLICATE
 * DONE        -> ARCHIVED, TODO (reopen)
 * CANCELLED   -> TODO (reopen), ARCHIVED
 * DUPLICATE   -> ARCHIVED
 * ARCHIVED    -> (terminal)
 * </pre>
 *
 * Notice BLOCKED has no edge to DONE — "blocked tasks cannot move to DONE"
 * is enforced by this graph's shape, not a separate runtime check.
 */
public final class TaskStatusTransitionValidator {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TaskStatus.BACKLOG, EnumSet.of(TaskStatus.TODO, TaskStatus.CANCELLED, TaskStatus.DUPLICATE));
        ALLOWED_TRANSITIONS.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BACKLOG, TaskStatus.CANCELLED, TaskStatus.DUPLICATE));
        ALLOWED_TRANSITIONS.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.TODO, TaskStatus.CANCELLED, TaskStatus.DUPLICATE));
        ALLOWED_TRANSITIONS.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED, TaskStatus.DUPLICATE));
        ALLOWED_TRANSITIONS.put(TaskStatus.IN_REVIEW, EnumSet.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED, TaskStatus.DUPLICATE));
        ALLOWED_TRANSITIONS.put(TaskStatus.DONE, EnumSet.of(TaskStatus.ARCHIVED, TaskStatus.TODO));
        ALLOWED_TRANSITIONS.put(TaskStatus.CANCELLED, EnumSet.of(TaskStatus.TODO, TaskStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(TaskStatus.DUPLICATE, EnumSet.of(TaskStatus.ARCHIVED));
        ALLOWED_TRANSITIONS.put(TaskStatus.ARCHIVED, EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStatusTransitionValidator() {
    }

    public static boolean isLegal(TaskStatus from, TaskStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to);
    }

    public static void assertLegal(TaskStatus from, TaskStatus to) {
        if (from == to) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "Task is already " + to + ".");
        }
        if (!isLegal(from, to)) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "Cannot move task from " + from + " to " + to + ".");
        }
    }
}
