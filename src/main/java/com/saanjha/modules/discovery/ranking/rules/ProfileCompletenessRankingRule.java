package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

@Component
public class ProfileCompletenessRankingRule implements RankingRule {

    @Override
    public String name() {
        return "profileCompleteness";
    }

    @Override
    public double score(RankingContext context) {
        return context.profileCompleteness() == null ? 0 : context.profileCompleteness();
    }
}
