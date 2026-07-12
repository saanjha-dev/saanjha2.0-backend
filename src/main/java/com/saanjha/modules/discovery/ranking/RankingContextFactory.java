package com.saanjha.modules.discovery.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@code ProjectSearchDocument}/{@code DeveloperSearchDocument}
 * (plus an optional raw search relevance score from the current query) into
 * a {@link RankingContext}. This is the one place that knows how to
 * normalize each raw stored value onto the [0, 1] scale every
 * {@link RankingRule} expects -- individual rules stay simple pass-throughs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingContextFactory {

    private final ObjectMapper objectMapper;

    public RankingContext forDeveloper(DeveloperSearchDocument doc, double searchRelevance) {
        List<Map<String, Object>> skills = readSkills(doc.getSkills());
        double verifiedRatio = skills.isEmpty() ? 0 :
                (double) skills.stream().filter(s -> Boolean.TRUE.equals(s.get("isVerified"))).count() / skills.size();

        Double reputationAvg = averageReputation(doc);
        double portfolioQuality = Math.min(1.0, doc.getPortfolioBadgeCount() / 10.0);
        double completeness = Math.min(1.0, Math.max(0.0, doc.getProfileScore() / 100.0));
        double activity = recencyScore(doc.getUpdatedAt(), 30);
        double freshness = recencyScore(doc.getCreatedAt(), 90);

        return new RankingContext(
                doc.getUserId(), "DEVELOPER", searchRelevance,
                verifiedRatio, doc.getContributionTotalScore(), reputationAvg,
                portfolioQuality, completeness, activity, freshness, Instant.now());
    }

    public RankingContext forProject(ProjectSearchDocument doc, double searchRelevance) {
        double fillRatio = doc.getMaxTeamSize() > 0
                ? (double) doc.getCurrentTeamSize() / doc.getMaxTeamSize() : 0;
        double freshness = recencyScore(doc.getPublishedAt(), 30);
        double activity = recencyScore(doc.getUpdatedAt(), 14);

        return new RankingContext(
                doc.getProjectId(), "PROJECT", searchRelevance,
                null, null, null,
                fillRatio, null, activity, freshness, Instant.now());
    }

    /** Reputation fields are Double (nullable) and, per Contribution's contract, already 0..100-ish; average and rescale to 0..1. */
    private Double averageReputation(DeveloperSearchDocument doc) {
        List<Double> present = java.util.stream.Stream.of(
                        doc.getReliabilityScore(), doc.getLeadershipScore(),
                        doc.getConsistencyScore(), doc.getReviewQualityScore())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (present.isEmpty()) {
            return null;
        }
        double avg = present.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.max(0, Math.min(1, avg / 100.0));
    }

    private double recencyScore(Instant timestamp, int halfLifeDays) {
        if (timestamp == null) {
            return 0;
        }
        long daysOld = Duration.between(timestamp, Instant.now()).toDays();
        return Math.max(0, 1.0 - ((double) daysOld / halfLifeDays));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSkills(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.debug("Discovery: could not parse developer skills JSON for ranking context.", e);
            return List.of();
        }
    }
}
