package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.TeamStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamStatusTransitionValidatorTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "CREATED, ACTIVE",
            "CREATED, ARCHIVED",
            "CREATED, DISSOLVED",
            "ACTIVE, LOCKED",
            "ACTIVE, ARCHIVED",
            "ACTIVE, DISSOLVED",
            "LOCKED, ACTIVE",
            "LOCKED, ARCHIVED",
            "LOCKED, DISSOLVED"
    })
    void allowsDocumentedTransitions(TeamStatus from, TeamStatus to) {
        assertThat(TeamStatusTransitionValidator.isLegal(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "CREATED, LOCKED",
            "ACTIVE, CREATED",
            "LOCKED, CREATED",
            "ARCHIVED, ACTIVE",
            "ARCHIVED, DISSOLVED",
            "DISSOLVED, ACTIVE",
            "DISSOLVED, ARCHIVED"
    })
    void rejectsUndocumentedTransitions(TeamStatus from, TeamStatus to) {
        assertThat(TeamStatusTransitionValidator.isLegal(from, to)).isFalse();
        assertThatThrownBy(() -> TeamStatusTransitionValidator.assertLegal(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void archivedAndDissolvedAreFullyTerminal() {
        for (TeamStatus target : TeamStatus.values()) {
            assertThat(TeamStatusTransitionValidator.isLegal(TeamStatus.ARCHIVED, target)).isFalse();
            assertThat(TeamStatusTransitionValidator.isLegal(TeamStatus.DISSOLVED, target)).isFalse();
        }
    }

    @Test
    void lockIsReversible() {
        assertThat(TeamStatusTransitionValidator.isLegal(TeamStatus.ACTIVE, TeamStatus.LOCKED)).isTrue();
        assertThat(TeamStatusTransitionValidator.isLegal(TeamStatus.LOCKED, TeamStatus.ACTIVE)).isTrue();
    }
}
