package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

/**
 * {@code ts_rank} values are small and effectively unbounded upward with
 * enough matching lexemes; a soft cap keeps one exceptionally dense match
 * from dominating every other signal the way a raw pass-through would.
 */
@Component
public class SearchRelevanceRankingRule implements RankingRule {

    private static final double SOFT_CAP = 0.25;

    @Override
    public String name() {
        return "searchRelevance";
    }

    @Override
    public double score(RankingContext context) {
        if (context.searchRelevance() == null || context.searchRelevance() <= 0) {
            return 0;
        }
        return context.searchRelevance() / (context.searchRelevance() + SOFT_CAP);
    }
}
