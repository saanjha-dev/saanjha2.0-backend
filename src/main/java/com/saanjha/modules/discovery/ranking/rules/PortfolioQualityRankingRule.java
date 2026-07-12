package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

@Component
public class PortfolioQualityRankingRule implements RankingRule {

    @Override
    public String name() {
        return "portfolioQuality";
    }

    @Override
    public double score(RankingContext context) {
        return context.portfolioQualityScore() == null ? 0 : context.portfolioQualityScore();
    }
}
