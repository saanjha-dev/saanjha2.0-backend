package com.saanjha.modules.discovery.recommendation.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.entity.RecommendationType;
import com.saanjha.modules.discovery.recommendation.RecommendationRequest;
import com.saanjha.modules.discovery.recommendation.RecommendationResult;
import com.saanjha.modules.discovery.recommendation.RecommendationResult.Item;
import com.saanjha.modules.discovery.recommendation.RecommendationStrategy;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Recommends projects whose required skills overlap with the requesting
 * developer's own skills. Deliberately does not check "is this developer
 * already a member of this project" -- Discovery does not own Team's
 * membership data and has no event today that would let it maintain that
 * exclusion without a cross-schema read. Documented limitation: a developer
 * may see a project they're already on recommended back to them.
 */
@Component
@RequiredArgsConstructor
public class SkillOverlapProjectRecommendationStrategy implements RecommendationStrategy {

    private final DeveloperSearchDocumentRepository developerRepository;
    private final ProjectSearchDocumentRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationType supports() {
        return RecommendationType.PROJECTS;
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        DeveloperSearchDocument developer = developerRepository.findById(request.userId()).orElse(null);
        if (developer == null) {
            return new RecommendationResult(List.of(), false);
        }

        List<String> skillNames = readSkillNames(developer.getSkills());
        List<ProjectSearchDocument> candidates = projectRepository.findByAnyRequiredSkill(skillNames, request.limit());

        List<Item> items = candidates.stream()
                .map(p -> new Item(p.getProjectId(), p.getTitle(), p.getPopularityScore(),
                        "Matches your skills: " + overlappingSkills(skillNames, p.getRequiredSkillsJson())))
                .toList();

        return new RecommendationResult(items, false);
    }

    private String overlappingSkills(List<String> developerSkills, String requiredSkillsJson) {
        List<String> required = readStringList(requiredSkillsJson);
        return developerSkills.stream().filter(required::contains)
                .reduce((a, b) -> a + ", " + b).orElse("");
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

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
