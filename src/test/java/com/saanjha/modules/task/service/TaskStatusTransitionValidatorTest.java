package com.saanjha.modules.task.service;

import com.saanjha.modules.task.entity.TaskStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatusTransitionValidatorTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "BACKLOG, TODO",
            "BACKLOG, CANCELLED",
            "BACKLOG, DUPLICATE",
            "TODO, IN_PROGRESS",
            "TODO, BACKLOG",
            "TODO, CANCELLED",
            "IN_PROGRESS, IN_REVIEW",
            "IN_PROGRESS, BLOCKED",
            "IN_PROGRESS, TODO",
            "BLOCKED, IN_PROGRESS",
            "BLOCKED, CANCELLED",
            "IN_REVIEW, DONE",
            "IN_REVIEW, IN_PROGRESS",
            "DONE, ARCHIVED",
            "DONE, TODO",
            "CANCELLED, TODO",
            "CANCELLED, ARCHIVED",
            "DUPLICATE, ARCHIVED"
    })
    void allowsDocumentedTransitions(TaskStatus from, TaskStatus to) {
        assertThat(TaskStatusTransitionValidator.isLegal(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "BLOCKED, DONE",
            "IN_PROGRESS, DONE",
            "BACKLOG, DONE",
            "BACKLOG, IN_PROGRESS",
            "ARCHIVED, TODO",
            "DUPLICATE, TODO",
            "DONE, IN_PROGRESS"
    })
    void rejectsUndocumentedTransitions(TaskStatus from, TaskStatus to) {
        assertThat(TaskStatusTransitionValidator.isLegal(from, to)).isFalse();
        assertThatThrownBy(() -> TaskStatusTransitionValidator.assertLegal(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void blockedTasksCanNeverReachDoneDirectly_structurallyNotJustByRuntimeCheck() {
        for (TaskStatus target : TaskStatus.values()) {
            if (target == TaskStatus.DONE) {
                assertThat(TaskStatusTransitionValidator.isLegal(TaskStatus.BLOCKED, target)).isFalse();
            }
        }
    }

    @Test
    void archivedIsFullyTerminal() {
        for (TaskStatus target : TaskStatus.values()) {
            assertThat(TaskStatusTransitionValidator.isLegal(TaskStatus.ARCHIVED, target)).isFalse();
        }
    }
}
