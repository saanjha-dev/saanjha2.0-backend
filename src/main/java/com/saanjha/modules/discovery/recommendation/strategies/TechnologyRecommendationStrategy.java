package com.saanjha.modules.discovery.recommendation.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.entity.TechnologyStat;
import com.saanjha.modules.discovery.recommendation.RecommendationRequest;
import com.saanjha.modules.discovery.recommendation.RecommendationResult;
import com.saanjha.modules.discovery.recommendation.RecommendationResult.Item;
import com.saanjha.modules.discovery.recommendation.RecommendationStrategy;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.TechnologyStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recommends trending technologies the requesting developer doesn't already list -- a "grow your stack" nudge. */
@Component
@RequiredArgsConstructor
public class TechnologyRecommendationStrategy implements RecommendationStrategy {

    private final TechnologyStatRepository technologyStatRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationType supports() {
        return RecommendationType.TECHNOLOGIES;
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        List<String> knownSkills = developerRepository.findById(request.userId())
                .map(DeveloperSearchDocument::getSkills)
                .map(this::readSkillNames)
                .orElse(List.of());

        List<TechnologyStat> trending = technologyStatRepository
                .findAllByOrderByTrendingScoreDesc(PageRequest.of(0, request.limit() + knownSkills.size()));

        List<Item> items = trending.stream()
                .filter(t -> !knownSkills.contains(t.getTechnologyName()))
                .limit(request.limit())
                .map(t -> new Item((UUID) null, t.getTechnologyName(), t.getTrendingScore(),
                        t.getProjectCount() + " active projects require it"))
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
