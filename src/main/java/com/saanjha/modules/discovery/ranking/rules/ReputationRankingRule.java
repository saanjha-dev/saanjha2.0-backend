package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

/** Reads the already-normalized (0..1) reputation average built by {@code RankingContextFactory}. */
@Component
public class ReputationRankingRule implements RankingRule {

    @Override
    public String name() {
        return "reputation";
    }

    @Override
    public double score(RankingContext context) {
        return context.reputationScore() == null ? 0 : context.reputationScore();
    }
}
