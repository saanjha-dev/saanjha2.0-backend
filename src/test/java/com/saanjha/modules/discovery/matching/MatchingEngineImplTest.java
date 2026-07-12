package com.saanjha.modules.discovery.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingEngineImplTest {

    @Mock private ProjectSearchDocumentRepository projectRepository;
    @Mock private DeveloperSearchDocumentRepository developerRepository;

    private MatchingEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngineImpl(projectRepository, developerRepository, new ObjectMapper());
    }

    @Test
    void higherSkillOverlapAndVerification_ranksAboveLowerOverlap() {
        UUID projectId = UUID.randomUUID();
        ProjectSearchDocument project = new ProjectSearchDocument();
        project.setProjectId(projectId);
        project.setLeadUserId(UUID.randomUUID());
        project.setRequiredSkillsJson("[\"Java\",\"PostgreSQL\",\"Kafka\"]");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        DeveloperSearchDocument strongMatch = developer("Strong Match",
                "[{\"skillName\":\"Java\",\"isVerified\":true},{\"skillName\":\"PostgreSQL\",\"isVerified\":true}]", 0);
        DeveloperSearchDocument weakMatch = developer("Weak Match",
                "[{\"skillName\":\"Java\",\"isVerified\":false}]", 0);
        when(developerRepository.findByAnySkill(any(), any(), anyInt()))
                .thenReturn(List.of(weakMatch, strongMatch));

        List<MatchingCandidate> results = engine.matchDevelopersToProject(projectId, 10);

        assertThat(results).extracting(MatchingCandidate::displayName)
                .containsExactly("Strong Match", "Weak Match");
    }

    @Test
    void unknownProject_returnsEmptyList() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThat(engine.matchDevelopersToProject(projectId, 10)).isEmpty();
    }

    private DeveloperSearchDocument developer(String name, String skillsJson, double contribution) {
        DeveloperSearchDocument doc = new DeveloperSearchDocument();
        doc.setUserId(UUID.randomUUID());
        doc.setDisplayName(name);
        doc.setSkills(skillsJson);
        doc.setContributionTotalScore(contribution);
        return doc;
    }
}
