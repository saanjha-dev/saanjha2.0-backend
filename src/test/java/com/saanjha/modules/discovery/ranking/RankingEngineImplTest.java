package com.saanjha.modules.discovery.ranking;

import com.saanjha.modules.discovery.ranking.rules.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies composition/weighting behavior, not the domain meaning of any
 * one signal (that's covered by RankingContextFactoryTest and each rule
 * being a one-line pass-through).
 */
class RankingEngineImplTest {

    private final RankingRuleWeights weights = new RankingRuleWeights();
    private final RankingEngineImpl engine = new RankingEngineImpl(
            List.of(new VerifiedSkillsRankingRule(), new ContributionRankingRule(), new ReputationRankingRule(),
                    new PortfolioQualityRankingRule(), new ProfileCompletenessRankingRule(),
                    new ActivityRankingRule(), new FreshnessRankingRule(), new SearchRelevanceRankingRule()),
            weights);

    @Test
    void allSignalsPresent_producesNonZeroTotalWithFullBreakdown() {
        RankingContext context = new RankingContext(
                java.util.UUID.randomUUID(), "DEVELOPER", 0.1, 1.0, 200.0, 0.8, 0.5, 0.9, 0.6, 0.7, Instant.now());

        RankingScore score = engine.rank(context);

        assertThat(score.total()).isGreaterThan(0).isLessThanOrEqualTo(1.0);
        assertThat(score.breakdown()).containsKeys(
                "verifiedSkills", "contribution", "reputation", "portfolioQuality",
                "profileCompleteness", "activity", "freshness", "searchRelevance");
    }

    @Test
    void missingSignals_contributeZeroRatherThanThrowing() {
        RankingContext sparse = new RankingContext(
                java.util.UUID.randomUUID(), "PROJECT", null, null, null, null, 0.4, null, 0.3, 0.5, Instant.now());

        RankingScore score = engine.rank(sparse);

        assertThat(score.breakdown().get("verifiedSkills")).isEqualTo(0.0);
        assertThat(score.breakdown().get("contribution")).isEqualTo(0.0);
        assertThat(score.breakdown().get("portfolioQuality")).isEqualTo(0.4);
        assertThat(score.total()).isGreaterThan(0);
    }

    @Test
    void configuredWeight_increasesThatRulesInfluenceOnTotal() {
        // Explicitly pass 0.0 instead of 0
        RankingContext context = new RankingContext(
                java.util.UUID.randomUUID(), "DEVELOPER", 0.0, 1.0, null, null, null, null, null, null, Instant.now());

        RankingScore baseline = engine.rank(context);

        weights.getWeights().put("verifiedSkills", 5.0);
        RankingScore boosted = engine.rank(context);

        assertThat(boosted.total()).isGreaterThan(baseline.total());
    }
}
