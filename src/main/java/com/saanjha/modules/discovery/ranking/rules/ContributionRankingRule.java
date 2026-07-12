package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

/**
 * Normalizes Contribution's raw running score onto [0, 1] via a soft cap
 * rather than a hard clip, so a very high scorer still ranks above a
 * merely-high one instead of both saturating at 1.0 identically.
 */
@Component
public class ContributionRankingRule implements RankingRule {

    private static final double SOFT_CAP = 500.0;

    @Override
    public String name() {
        return "contribution";
    }

    @Override
    public double score(RankingContext context) {
        if (context.contributionScore() == null || context.contributionScore() <= 0) {
            return 0;
        }
        return context.contributionScore() / (context.contributionScore() + SOFT_CAP);
    }
}
