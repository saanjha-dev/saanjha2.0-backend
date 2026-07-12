package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

@Component
public class ActivityRankingRule implements RankingRule {

    @Override
    public String name() {
        return "activity";
    }

    @Override
    public double score(RankingContext context) {
        return context.activityScore() == null ? 0 : context.activityScore();
    }
}
