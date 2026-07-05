package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipStatusTransitionValidatorTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "ACTIVE, LEFT",
            "ACTIVE, REMOVED",
            "ACTIVE, SUSPENDED",
            "ACTIVE, ARCHIVED",
            "SUSPENDED, ACTIVE",
            "SUSPENDED, REMOVED",
            "SUSPENDED, ARCHIVED"
    })
    void allowsDocumentedTransitions(MembershipStatus from, MembershipStatus to) {
        assertThat(MembershipStatusTransitionValidator.isLegal(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "REMOVED, SUSPENDED",
            "REMOVED, ACTIVE",
            "LEFT, ACTIVE",
            "LEFT, SUSPENDED",
            "ARCHIVED, ACTIVE"
    })
    void rejectsUndocumentedTransitions(MembershipStatus from, MembershipStatus to) {
        assertThat(MembershipStatusTransitionValidator.isLegal(from, to)).isFalse();
        assertThatThrownBy(() -> MembershipStatusTransitionValidator.assertLegal(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void leftAndRemovedAndArchivedAreFullyTerminal() {
        for (MembershipStatus target : MembershipStatus.values()) {
            assertThat(MembershipStatusTransitionValidator.isLegal(MembershipStatus.LEFT, target)).isFalse();
            assertThat(MembershipStatusTransitionValidator.isLegal(MembershipStatus.REMOVED, target)).isFalse();
            assertThat(MembershipStatusTransitionValidator.isLegal(MembershipStatus.ARCHIVED, target)).isFalse();
        }
    }

    @Test
    void suspensionIsReversible() {
        assertThat(MembershipStatusTransitionValidator.isLegal(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED)).isTrue();
        assertThat(MembershipStatusTransitionValidator.isLegal(MembershipStatus.SUSPENDED, MembershipStatus.ACTIVE)).isTrue();
    }
}
