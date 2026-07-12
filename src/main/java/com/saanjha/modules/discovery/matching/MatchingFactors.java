package com.saanjha.modules.discovery.matching;

/** Raw, pre-weighting inputs to a single developer/project match calculation. */
public record MatchingFactors(
        double skillOverlapRatio,   // overlapping required skills / total required skills, 0..1
        double verifiedOverlapBonus,// fraction of the overlap that's verified, 0..1
        double reputationScore,     // 0..1 normalized average (see RankingContextFactory)
        double contributionScore    // 0..1 soft-capped normalized running score
) {}
