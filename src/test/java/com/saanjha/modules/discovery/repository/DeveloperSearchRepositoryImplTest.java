package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.search.DeveloperSearchFilters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
class DeveloperSearchRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private DeveloperSearchDocumentRepository repository;

    @Test
    void keywordSearch_matchesHandleAndDisplayName() {
        repository.save(developer("Asha Verma", "asha_codes", "SENIOR", skillsJson("Java", true), 90));
        repository.save(developer("Ravi Kumar", "ravi_dev", "JUNIOR", skillsJson("Python", false), 60));

        Page<DeveloperSearchDocument> results = repository.search(
                new DeveloperSearchFilters("asha", List.of(), null, null, null, null, null), PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(DeveloperSearchDocument::getUniqueHandle)
                .containsExactly("asha_codes");
    }

    @Test
    void verifiedSkillsOnlyFilter_excludesUnverifiedMatches() {
        repository.save(developer("Verified Dev", "verified_dev", "SENIOR", skillsJson("Rust", true), 80));
        repository.save(developer("Unverified Dev", "unverified_dev", "SENIOR", skillsJson("Rust", false), 80));

        Page<DeveloperSearchDocument> results = repository.search(
                new DeveloperSearchFilters(null, List.of("Rust"), null, true, null, null, null), PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(DeveloperSearchDocument::getUniqueHandle)
                .containsExactly("verified_dev");
    }

    @Test
    void minProfileScoreFilter_excludesLowerScores() {
        repository.save(developer("High Score", "high_score", "SENIOR", skillsJson("Go", true), 95));
        repository.save(developer("Low Score", "low_score", "JUNIOR", skillsJson("Go", true), 30));

        Page<DeveloperSearchDocument> results = repository.search(
                new DeveloperSearchFilters(null, List.of(), null, null, 80, null, null), PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(DeveloperSearchDocument::getUniqueHandle)
                .containsExactly("high_score");
    }

    private DeveloperSearchDocument developer(String name, String handle, String level, String skillsJson, int profileScore) {
        DeveloperSearchDocument doc = new DeveloperSearchDocument();
        doc.setUserId(UUID.randomUUID());
        doc.setDisplayName(name);
        doc.setUniqueHandle(handle);
        doc.setHeadline(name + "'s headline");
        doc.setExperienceLevel(level);
        doc.setSkills(skillsJson);
        doc.setInterests("[]");
        doc.setProfileScore(profileScore);
        doc.setDeleted(false);
        return doc;
    }

    private String skillsJson(String skillName, boolean verified) {
        return "[{\"skillName\":\"" + skillName + "\",\"skillLevel\":\"ADVANCED\",\"isVerified\":" + verified + "}]";
    }
}
