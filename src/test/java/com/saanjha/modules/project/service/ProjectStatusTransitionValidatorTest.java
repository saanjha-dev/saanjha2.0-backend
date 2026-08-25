package com.saanjha.modules.project.service;

import com.saanjha.modules.project.entity.ProjectStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectStatusTransitionValidatorTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "DRAFT, RECRUITING",
            "DRAFT, ARCHIVED",
            "RECRUITING, IN_PROGRESS",
            "RECRUITING, ARCHIVED",
            "IN_PROGRESS, COMPLETED",
            "IN_PROGRESS, ARCHIVED",
            "COMPLETED, ARCHIVED"
    })
    void allowsDocumentedTransitions(ProjectStatus from, ProjectStatus to) {
        assertThat(ProjectStatusTransitionValidator.isLegal(from, to)).isTrue();
        assertThatCode(() -> ProjectStatusTransitionValidator.assertLegal(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "DRAFT, IN_PROGRESS",
            "DRAFT, COMPLETED",
            "RECRUITING, COMPLETED",
            "RECRUITING, DRAFT",
            "IN_PROGRESS, DRAFT",
            "IN_PROGRESS, RECRUITING",
            "COMPLETED, DRAFT",
            "ARCHIVED, DRAFT",
            "ARCHIVED, RECRUITING"
    })
    void rejectsUndocumentedTransitions(ProjectStatus from, ProjectStatus to) {
        assertThat(ProjectStatusTransitionValidator.isLegal(from, to)).isFalse();
        assertThatThrownBy(() -> ProjectStatusTransitionValidator.assertLegal(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void terminalStatesHaveNoOutboundTransitions() {
        for (ProjectStatus target : ProjectStatus.values()) {
            if (target != ProjectStatus.ARCHIVED) {
                assertThat(ProjectStatusTransitionValidator.isLegal(ProjectStatus.COMPLETED, target)).isFalse();
            }
            assertThat(ProjectStatusTransitionValidator.isLegal(ProjectStatus.ARCHIVED, target)).isFalse();
        }
    }

    @Test
    void selfTransitionIsRejectedEvenWhenTargetEqualsCurrent() {
        assertThatThrownBy(() -> ProjectStatusTransitionValidator.assertLegal(ProjectStatus.RECRUITING, ProjectStatus.RECRUITING))
                .isInstanceOf(AppException.class);
    }
}
