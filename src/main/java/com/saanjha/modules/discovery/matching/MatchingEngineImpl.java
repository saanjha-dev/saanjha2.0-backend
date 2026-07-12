package com.saanjha.modules.discovery.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default heuristic implementation. Weights here are intentionally simpler
 * (fixed, not externally configured) than the Ranking Engine's -- matching
 * is a narrower, project-scoped question than general search ranking, and
 * the brief only asks that the *algorithm* be swappable (via the
 * {@link MatchingEngine} interface), not that its weights be individually
 * tunable in application.yml the way ranking's are.
 */
@Service
@RequiredArgsConstructor
public class MatchingEngineImpl implements MatchingEngine {

    private static final double SKILL_OVERLAP_WEIGHT = 0.5;
    private static final double VERIFIED_BONUS_WEIGHT = 0.15;
    private static final double REPUTATION_WEIGHT = 0.2;
    private static final double CONTRIBUTION_WEIGHT = 0.15;
    private static final double CONTRIBUTION_SOFT_CAP = 500.0;

    private final ProjectSearchDocumentRepository projectRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<MatchingCandidate> matchDevelopersToProject(UUID projectId, int limit) {
        ProjectSearchDocument project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return List.of();
        }

        List<String> requiredSkills = readStringList(project.getRequiredSkillsJson());
        if (requiredSkills.isEmpty()) {
            return List.of();
        }

        int candidatePoolSize = Math.min(limit * 3, 300);
        List<DeveloperSearchDocument> candidates =
                developerRepository.findByAnySkill(requiredSkills, project.getLeadUserId(), candidatePoolSize);

        return candidates.stream()
                .map(dev -> score(dev, requiredSkills))
                .sorted((a, b) -> Double.compare(b.score().total(), a.score().total()))
                .limit(limit)
                .toList();
    }

    private MatchingCandidate score(DeveloperSearchDocument developer, List<String> requiredSkills) {
        List<Map<String, Object>> devSkills = readSkillObjects(developer.getSkills());

        long overlapCount = devSkills.stream()
                .filter(s -> requiredSkills.contains(String.valueOf(s.get("skillName"))))
                .count();
        long verifiedOverlapCount = devSkills.stream()
                .filter(s -> requiredSkills.contains(String.valueOf(s.get("skillName")))
                        && Boolean.TRUE.equals(s.get("isVerified")))
                .count();

        double skillOverlapRatio = requiredSkills.isEmpty() ? 0 : (double) overlapCount / requiredSkills.size();
        double verifiedBonus = overlapCount == 0 ? 0 : (double) verifiedOverlapCount / overlapCount;

        Double reputationAvg = averageReputation(developer);
        double reputationScore = reputationAvg == null ? 0 : reputationAvg;
        double contributionScore = developer.getContributionTotalScore() <= 0 ? 0 :
                developer.getContributionTotalScore() / (developer.getContributionTotalScore() + CONTRIBUTION_SOFT_CAP);

        double total = (skillOverlapRatio * SKILL_OVERLAP_WEIGHT)
                + (verifiedBonus * VERIFIED_BONUS_WEIGHT)
                + (reputationScore * REPUTATION_WEIGHT)
                + (contributionScore * CONTRIBUTION_WEIGHT);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("skillOverlap", skillOverlapRatio);
        breakdown.put("verifiedBonus", verifiedBonus);
        breakdown.put("reputation", reputationScore);
        breakdown.put("contribution", contributionScore);

        return new MatchingCandidate(developer.getUserId(), developer.getDisplayName(),
                new MatchingScore(total, breakdown));
    }

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

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSkillObjects(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
