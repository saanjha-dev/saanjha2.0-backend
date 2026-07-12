package com.saanjha.modules.discovery.ranking;

import java.time.Instant;
import java.util.UUID;

/**
 * A flat bag of pre-normalized-ish signals for one entity (project or
 * developer) being ranked. Built by {@code RankingContextFactory} from a
 * {@code ProjectSearchDocument}/{@code DeveloperSearchDocument} plus whatever
 * the current query contributed (search relevance). Fields that don't apply
 * to a given entity type are {@code null}, and every {@link RankingRule}
 * must treat {@code null} as "no signal" (contribute 0), never as a
 * meaningful zero on whatever scale that field uses.
 */
public record RankingContext(
        UUID entityId,
        String entityType,               // "PROJECT" or "DEVELOPER"
        Double searchRelevance,          // ts_rank from the query, 0 if this wasn't a keyword search
        Double verifiedSkillRatio,       // developer only: verified skills / total skills, 0..1
        Double contributionScore,        // developer only: raw running total from Contribution
        Double reputationScore,          // developer only: 0..1 normalized average of the four Contribution reputation signals
        Double portfolioQualityScore,    // developer only: 0..1 normalized badge count
        Double profileCompleteness,      // developer only: 0..1 (profileScore / 100)
        Double activityScore,            // both: 0..1 recency-of-update proxy
        Double freshnessScore,           // both: 0..1 recency-of-creation/publish proxy
        Instant evaluatedAt
) {}
