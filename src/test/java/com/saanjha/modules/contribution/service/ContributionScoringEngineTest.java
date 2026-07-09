package com.saanjha.modules.contribution.service;

import com.saanjha.modules.contribution.entity.IntegrityFlag;
import com.saanjha.modules.contribution.service.ContributionScoringEngine.ScoreResult;
import com.saanjha.modules.contribution.service.ContributionScoringEngine.TaskCompletionInputs;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContributionScoringEngineTest {

    private static final double BASE_WEIGHT = 10.0;

    @Test
    void plainCompletion_withNoExtras_scoresAtBaseWeight() {
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, false, 0, 0, null, null, 0);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.finalScore()).isEqualTo(BASE_WEIGHT);
        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.NONE);
        assertThat(result.explanation()).isNotEmpty();
    }

    @Test
    void higherStoryPoints_increasesScore() {
        TaskCompletionInputs low = new TaskCompletionInputs(1, null, null, 0, false, 0, 0, null, null, 0);
        TaskCompletionInputs high = new TaskCompletionInputs(8, null, null, 0, false, 0, 0, null, null, 0);

        double lowScore = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, low).finalScore();
        double highScore = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, high).finalScore();

        assertThat(highScore).isGreaterThan(lowScore);
    }

    @Test
    void selfReview_isFlaggedAndHeavilyDiscounted() {
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, true, 0, 0, null, null, 0);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.SELF_REVIEW);
        assertThat(result.finalScore()).isLessThan(BASE_WEIGHT);
    }

    @Test
    void instantCompletion_isFlaggedAsSuspiciousVelocity() {
        Instant started = Instant.now();
        Instant completed = started.plusSeconds(5);
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, false, 0, 0, started, completed, 0);

        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.SUSPICIOUS_VELOCITY);
        assertThat(result.finalScore()).isLessThan(BASE_WEIGHT * 0.5);
    }

    @Test
    void normalCompletionDuration_isNotFlagged() {
        Instant started = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant completed = Instant.now();
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, false, 0, 0, started, completed, 0);

        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.NONE);
    }

    @Test
    void excessiveReassignment_isFlaggedAsChurn() {
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, false, 5, 0, null, null, 0);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.REASSIGNMENT_CHURN);
    }

    @Test
    void excessiveReopens_isFlaggedAsReopenFarming() {
        TaskCompletionInputs inputs = new TaskCompletionInputs(null, null, null, 0, false, 0, 5, null, null, 0);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.integrityFlag()).isEqualTo(IntegrityFlag.REOPEN_FARMING);
    }

    @Test
    void qualityMultiplier_neverGoesBelowFloor() {
        // Stack every possible penalty to try to drive the multiplier negative.
        TaskCompletionInputs inputs = new TaskCompletionInputs(1, "LOW", 1.0, 100.0, true, 10, 10, Instant.now(), Instant.now().plusSeconds(1), 0);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(BASE_WEIGHT, inputs);

        assertThat(result.finalScore()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void leadershipSuccess_appliesSmallTeamBonus() {
        ScoreResult smallTeam = ContributionScoringEngine.scoreLeadership(15.0, new ContributionScoringEngine.LeadershipInputs(2, true));
        ScoreResult bigTeam = ContributionScoringEngine.scoreLeadership(15.0, new ContributionScoringEngine.LeadershipInputs(10, true));

        assertThat(smallTeam.finalScore()).isGreaterThan(bigTeam.finalScore());
    }
}
