package com.saanjha.modules.application.service;

import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTransitionValidatorTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "SUBMITTED, UNDER_REVIEW",
            "SUBMITTED, SHORTLISTED",
            "SUBMITTED, ACCEPTED",
            "SUBMITTED, REJECTED",
            "SUBMITTED, WITHDRAWN",
            "SUBMITTED, EXPIRED",
            "UNDER_REVIEW, SHORTLISTED",
            "UNDER_REVIEW, ACCEPTED",
            "UNDER_REVIEW, REJECTED",
            "UNDER_REVIEW, WITHDRAWN",
            "UNDER_REVIEW, EXPIRED",
            "SHORTLISTED, ACCEPTED",
            "SHORTLISTED, REJECTED",
            "SHORTLISTED, WITHDRAWN",
            "SHORTLISTED, EXPIRED",
            "REJECTED, UNDER_REVIEW"
    })
    void allowsDocumentedTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(ApplicationStatusTransitionValidator.isLegal(from, to)).isTrue();
        assertThatCode(() -> ApplicationStatusTransitionValidator.assertLegal(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "ACCEPTED, REJECTED",
            "ACCEPTED, WITHDRAWN",
            "WITHDRAWN, SUBMITTED",
            "WITHDRAWN, UNDER_REVIEW",
            "EXPIRED, UNDER_REVIEW",
            "REJECTED, ACCEPTED",
            "REJECTED, SHORTLISTED",
            "SUBMITTED, SUBMITTED"
    })
    void rejectsUndocumentedTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(ApplicationStatusTransitionValidator.isLegal(from, to)).isFalse();
        assertThatThrownBy(() -> ApplicationStatusTransitionValidator.assertLegal(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void acceptedWithdrawnAndExpiredAreFullyTerminal() {
        for (ApplicationStatus target : ApplicationStatus.values()) {
            assertThat(ApplicationStatusTransitionValidator.isLegal(ApplicationStatus.ACCEPTED, target)).isFalse();
            assertThat(ApplicationStatusTransitionValidator.isLegal(ApplicationStatus.WITHDRAWN, target)).isFalse();
            assertThat(ApplicationStatusTransitionValidator.isLegal(ApplicationStatus.EXPIRED, target)).isFalse();
        }
    }

    @Test
    void rejectedHasExactlyOneOutboundTransition_theReopenException() {
        long legalTargets = java.util.Arrays.stream(ApplicationStatus.values())
                .filter(target -> ApplicationStatusTransitionValidator.isLegal(ApplicationStatus.REJECTED, target))
                .count();
        assertThat(legalTargets).isEqualTo(1);
        assertThat(ApplicationStatusTransitionValidator.isLegal(ApplicationStatus.REJECTED, ApplicationStatus.UNDER_REVIEW)).isTrue();
    }
}
