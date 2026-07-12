package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

@Component
public class FreshnessRankingRule implements RankingRule {

    @Override
    public String name() {
        return "freshness";
    }

    @Override
    public double score(RankingContext context) {
        return context.freshnessScore() == null ? 0 : context.freshnessScore();
    }
}
