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

/**
 * "Who should I invite to this project?" -- requires
 * {@code contextProjectId} (see {@code RecommendationRequest}'s Javadoc);
 * without it, falls back to an empty result rather than guessing.
 */
@Component
@RequiredArgsConstructor
public class TeammateRecommendationStrategy implements RecommendationStrategy {

    private final ProjectSearchDocumentRepository projectRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationType supports() {
        return RecommendationType.TEAMMATES;
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        if (request.contextProjectId() == null) {
            return new RecommendationResult(List.of(), false);
        }
        ProjectSearchDocument project = projectRepository.findById(request.contextProjectId()).orElse(null);
        if (project == null) {
            return new RecommendationResult(List.of(), false);
        }

        List<String> requiredSkills = readStringList(project.getRequiredSkillsJson());
        List<DeveloperSearchDocument> candidates =
                developerRepository.findByAnySkill(requiredSkills, project.getLeadUserId(), request.limit());

        List<Item> items = candidates.stream()
                .map(d -> new Item(d.getUserId(), d.getDisplayName(), d.getProfileScore(),
                        "Skills overlap with " + project.getTitle() + "'s requirements"))
                .toList();

        return new RecommendationResult(items, false);
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
