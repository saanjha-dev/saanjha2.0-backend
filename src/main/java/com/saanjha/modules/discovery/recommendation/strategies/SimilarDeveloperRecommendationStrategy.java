package com.saanjha.modules.discovery.recommendation.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.recommendation.RecommendationRequest;
import com.saanjha.modules.discovery.recommendation.RecommendationResult;
import com.saanjha.modules.discovery.recommendation.RecommendationResult.Item;
import com.saanjha.modules.discovery.recommendation.RecommendationStrategy;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code RecommendationType.DEVELOPERS} without a project context means
 * "developers similar to me" (e.g. for a Builder wanting peers/role models),
 * as opposed to {@code TEAMMATES}, which is project-scoped. Kept as a
 * separate strategy/type rather than overloading TEAMMATES, since the two
 * have genuinely different intents even though the underlying skill-overlap
 * mechanism is similar.
 */
@Component
@RequiredArgsConstructor
public class SimilarDeveloperRecommendationStrategy implements RecommendationStrategy {

    private final DeveloperSearchDocumentRepository developerRepository;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationType supports() {
        return RecommendationType.DEVELOPERS;
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        DeveloperSearchDocument self = developerRepository.findById(request.userId()).orElse(null);
        if (self == null) {
            return new RecommendationResult(List.of(), false);
        }

        List<String> skillNames = readSkillNames(self.getSkills());
        List<DeveloperSearchDocument> candidates =
                developerRepository.findByAnySkill(skillNames, self.getUserId(), request.limit());

        List<Item> items = candidates.stream()
                .map(d -> new Item(d.getUserId(), d.getDisplayName(), d.getProfileScore(), "Shares skills with you"))
                .toList();

        return new RecommendationResult(items, false);
    }

    @SuppressWarnings("unchecked")
    private List<String> readSkillNames(String skillsJson) {
        try {
            List<Map<String, Object>> skills = objectMapper.readValue(skillsJson, List.class);
            return skills.stream().map(s -> String.valueOf(s.get("skillName"))).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
