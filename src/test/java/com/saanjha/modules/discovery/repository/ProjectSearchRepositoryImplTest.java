package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.search.ProjectSearchFilters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the V22 migration's tsvector trigger and JSONB containment
 * queries against a real PostgreSQL instance -- both are Postgres-specific
 * behaviors ({@code to_tsvector}, {@code @>}) that a mocked repository can't
 * meaningfully verify.
 */
@DataJpaTest
@Testcontainers
class ProjectSearchRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ProjectSearchDocumentRepository repository;

    @Test
    void keywordSearch_matchesTitleViaTsvector() {
        repository.save(project("Realtime Chess Engine", "chess-engine", "GAMES", "RECRUITING",
                List.of("Java", "WebSockets"), List.of("games"), 5, 2));
        repository.save(project("Personal Finance Tracker", "finance-tracker", "FINTECH", "RECRUITING",
                List.of("React", "Node"), List.of("web"), 4, 1));

        Page<ProjectSearchDocument> results = repository.search(
                new ProjectSearchFilters("chess", null, List.of(), List.of(), "RECRUITING", null),
                PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(ProjectSearchDocument::getTitle)
                .containsExactly("Realtime Chess Engine");
    }

    @Test
    void requiredSkillsFilter_requiresAllListedSkills() {
        repository.save(project("Full Stack App", "full-stack-app", "WEB", "RECRUITING",
                List.of("Java", "React", "PostgreSQL"), List.of(), 5, 1));
        repository.save(project("Frontend Only", "frontend-only", "WEB", "RECRUITING",
                List.of("React"), List.of(), 5, 1));

        Page<ProjectSearchDocument> results = repository.search(
                new ProjectSearchFilters(null, null, List.of("Java", "React"), List.of(), "RECRUITING", null),
                PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(ProjectSearchDocument::getSlug)
                .containsExactly("full-stack-app");
    }

    @Test
    void hasOpenSlotsFilter_excludesFullTeams() {
        repository.save(project("Full Team", "full-team", "WEB", "RECRUITING", List.of("Java"), List.of(), 2, 2));
        repository.save(project("Open Team", "open-team", "WEB", "RECRUITING", List.of("Java"), List.of(), 5, 2));

        Page<ProjectSearchDocument> results = repository.search(
                new ProjectSearchFilters(null, null, List.of(), List.of(), "RECRUITING", true),
                PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(ProjectSearchDocument::getSlug).containsExactly("open-team");
    }

    @Test
    void findByAnyRequiredSkill_matchesOnPartialOverlap() {
        repository.save(project("Java Backend", "java-backend", "WEB", "RECRUITING",
                List.of("Java", "Spring"), List.of(), 5, 1));
        repository.save(project("Rust CLI", "rust-cli", "TOOLS", "RECRUITING",
                List.of("Rust"), List.of(), 5, 1));

        List<ProjectSearchDocument> matches = repository.findByAnyRequiredSkill(List.of("Java", "Kotlin"), 10);

        assertThat(matches).extracting(ProjectSearchDocument::getSlug).containsExactly("java-backend");
    }

    private ProjectSearchDocument project(String title, String slug, String category, String status,
                                           List<String> skills, List<String> tags, int maxTeamSize, int currentTeamSize) {
        ProjectSearchDocument doc = new ProjectSearchDocument();
        doc.setProjectId(UUID.randomUUID());
        doc.setLeadUserId(UUID.randomUUID());
        doc.setTitle(title);
        doc.setSlug(slug);
        doc.setDescriptionExcerpt("A project about " + title);
        doc.setCategory(category);
        doc.setVisibility("PUBLIC");
        doc.setStatus(status);
        doc.setRequiredSkillsJson(toJsonArray(skills));
        doc.setTagsJson(toJsonArray(tags));
        doc.setMaxTeamSize(maxTeamSize);
        doc.setCurrentTeamSize(currentTeamSize);
        doc.setIndexed(true);
        doc.setPublishedAt(Instant.now());
        return doc;
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i)).append("\"");
        }
        return sb.append("]").toString();
    }
}
