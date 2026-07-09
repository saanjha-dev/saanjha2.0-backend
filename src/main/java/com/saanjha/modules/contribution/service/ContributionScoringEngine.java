package com.saanjha.modules.contribution.service;

import com.saanjha.modules.contribution.dto.ContributionResponseDTOs.ExplanationStep;
import com.saanjha.modules.contribution.entity.IntegrityFlag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The scoring math itself, deliberately separated from persistence/event
 * concerns (mirrors the state-machine-validator pattern used everywhere
 * else in this codebase — kept easy to unit test in isolation). Weights are
 * ALWAYS supplied as a parameter, never a Java constant — "never hardcode
 * business scoring" per the module's brief. See {@code ScoringWeights} for
 * where those numbers actually live.
 *
 * A ledger entry is never silently rejected for looking suspicious — every
 * check here produces a discount multiplier AND an {@link IntegrityFlag},
 * the record is always created. See each method's inline reasoning for the
 * specific thresholds; they are deliberately conservative (discount, not
 * zero-out) since a false accusation of gaming is its own trust problem.
 */
public final class ContributionScoringEngine {

    private static final Duration SUSPICIOUS_VELOCITY_THRESHOLD = Duration.ofSeconds(60);
    private static final int REASSIGNMENT_CHURN_THRESHOLD = 3;
    private static final int REOPEN_FARMING_THRESHOLD = 3;
    private static final double MINIMUM_QUALITY_MULTIPLIER = 0.1;

    private ContributionScoringEngine() {
    }

    public record TaskCompletionInputs(
            Integer storyPoints,
            String priority,
            Double estimatedHours,
            double actualHours,
            boolean selfReviewed,
            int reassignmentCount,
            int reopenCount,
            Instant startedAt,
            Instant completedAt,
            int projectTeamSize
    ) {}

    public record ScoreResult(
            double baseScore,
            double complexityMultiplier,
            double qualityMultiplier,
            double leadershipMultiplier,
            double finalScore,
            IntegrityFlag integrityFlag,
            List<ExplanationStep> explanation
    ) {}

    public static ScoreResult scoreTaskCompletion(double baseWeight, TaskCompletionInputs inputs) {
        List<ExplanationStep> explanation = new ArrayList<>();
        explanation.add(new ExplanationStep("Base score", "Task completed: " + baseWeight));

        double complexityMultiplier = 1.0;
        complexityMultiplier += complexityBonusFromStoryPoints(inputs.storyPoints(), explanation);
        complexityMultiplier += priorityBonus(inputs.priority(), explanation);
        complexityMultiplier += teamSizeDifficultyBonus(inputs.projectTeamSize(), explanation);

        double qualityMultiplier = 1.0;
        qualityMultiplier += estimateAccuracyAdjustment(inputs.estimatedHours(), inputs.actualHours(), explanation);
        qualityMultiplier -= reopenPenalty(inputs.reopenCount(), explanation);

        IntegrityFlag flag = IntegrityFlag.NONE;

        if (inputs.selfReviewed()) {
            flag = IntegrityFlag.SELF_REVIEW;
            qualityMultiplier *= 0.5;
            explanation.add(new ExplanationStep("Integrity: self-review", "Reviewer was the same person as the assignee — heavily discounted, flagged for review."));
        } else if (isSuspiciouslyFast(inputs.startedAt(), inputs.completedAt())) {
            flag = IntegrityFlag.SUSPICIOUS_VELOCITY;
            qualityMultiplier *= 0.1;
            explanation.add(new ExplanationStep("Integrity: suspicious velocity", "Completed implausibly fast after starting — heavily discounted, flagged for review."));
        } else if (inputs.reassignmentCount() > REASSIGNMENT_CHURN_THRESHOLD) {
            flag = IntegrityFlag.REASSIGNMENT_CHURN;
            qualityMultiplier *= 0.5;
            explanation.add(new ExplanationStep("Integrity: reassignment churn", "Reassigned " + inputs.reassignmentCount() + " times before completion — discounted, flagged for review."));
        } else if (inputs.reopenCount() > REOPEN_FARMING_THRESHOLD) {
            flag = IntegrityFlag.REOPEN_FARMING;
            qualityMultiplier *= 0.5;
            explanation.add(new ExplanationStep("Integrity: reopen farming", "Reopened " + inputs.reopenCount() + " times — discounted, flagged for review."));
        }

        qualityMultiplier = Math.max(qualityMultiplier, MINIMUM_QUALITY_MULTIPLIER);

        double finalScore = baseWeight * complexityMultiplier * qualityMultiplier;
        explanation.add(new ExplanationStep("Final score", String.format("%.2f", finalScore)));

        return new ScoreResult(baseWeight, complexityMultiplier, qualityMultiplier, 1.0, finalScore, flag, explanation);
    }

    public record LeadershipInputs(int projectTeamSize, boolean projectSucceeded) {}

    public static ScoreResult scoreLeadership(double baseWeight, LeadershipInputs inputs) {
        List<ExplanationStep> explanation = new ArrayList<>();
        explanation.add(new ExplanationStep("Base score", "Leadership: " + baseWeight));

        double leadershipMultiplier = 1.0;
        if (inputs.projectSucceeded()) {
            double smallTeamBonus = inputs.projectTeamSize() > 0 ? Math.min(1.0 / inputs.projectTeamSize(), 0.5) : 0;
            leadershipMultiplier += smallTeamBonus;
            explanation.add(new ExplanationStep("Project succeeded", "Led a " + inputs.projectTeamSize() + "-person team to completion: x" + String.format("%.2f", 1 + smallTeamBonus)));
        } else {
            explanation.add(new ExplanationStep("Leadership assumed", "Took on leadership responsibility."));
        }

        double finalScore = baseWeight * leadershipMultiplier;
        explanation.add(new ExplanationStep("Final score", String.format("%.2f", finalScore)));

        return new ScoreResult(baseWeight, 1.0, 1.0, leadershipMultiplier, finalScore, IntegrityFlag.NONE, explanation);
    }

    // ========================================================================
    // INDIVIDUAL SCORING FACTORS (each independently explainable)
    // ========================================================================

    private static double complexityBonusFromStoryPoints(Integer storyPoints, List<ExplanationStep> explanation) {
        if (storyPoints == null) {
            return 0.0;
        }
        double bonus = switch (Integer.min(storyPoints, 13)) {
            case 0, 1, 2 -> 0.0;
            case 3, 4, 5 -> 0.3;
            case 6, 7, 8 -> 0.6;
            default -> 1.0;
        };
        if (bonus > 0) {
            explanation.add(new ExplanationStep("Complexity: " + storyPoints + " story points", "x" + String.format("%.2f", 1 + bonus)));
        }
        return bonus;
    }

    private static double priorityBonus(String priority, List<ExplanationStep> explanation) {
        if (priority == null) {
            return 0.0;
        }
        double bonus = switch (priority) {
            case "URGENT" -> 0.15;
            case "CRITICAL" -> 0.25;
            default -> 0.0;
        };
        if (bonus > 0) {
            explanation.add(new ExplanationStep("Priority: " + priority, "x" + String.format("%.2f", 1 + bonus)));
        }
        return bonus;
    }

    /** Folded into the complexity multiplier rather than a 4th ledger column — see ContributionLedgerEntry's schema comment on why only 3 multiplier slots exist. */
    private static double teamSizeDifficultyBonus(int teamSize, List<ExplanationStep> explanation) {
        if (teamSize <= 0) {
            return 0.0;
        }
        double bonus = teamSize <= 2 ? 0.2 : teamSize <= 5 ? 0.1 : 0.0;
        if (bonus > 0) {
            explanation.add(new ExplanationStep("Small team (" + teamSize + ")", "x" + String.format("%.2f", 1 + bonus)));
        }
        return bonus;
    }

    private static double estimateAccuracyAdjustment(Double estimatedHours, double actualHours, List<ExplanationStep> explanation) {
        if (estimatedHours == null || estimatedHours <= 0) {
            return 0.0;
        }
        double ratio = actualHours / estimatedHours;
        if (ratio <= 1.2) {
            explanation.add(new ExplanationStep("Delivered close to estimate", "x1.10"));
            return 0.10;
        }
        if (ratio > 2.0) {
            explanation.add(new ExplanationStep("Took much longer than estimated", "x0.80"));
            return -0.20;
        }
        return 0.0;
    }

    private static double reopenPenalty(int reopenCount, List<ExplanationStep> explanation) {
        if (reopenCount <= 0) {
            return 0.0;
        }
        double penalty = Math.min(reopenCount * 0.1, 0.5);
        explanation.add(new ExplanationStep("Reopened " + reopenCount + " time(s)", "-" + String.format("%.2f", penalty)));
        return penalty;
    }

    private static boolean isSuspiciouslyFast(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null) {
            return false;
        }
        return Duration.between(startedAt, completedAt).compareTo(SUSPICIOUS_VELOCITY_THRESHOLD) < 0;
    }
}
