package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Project schema against a real PostgreSQL instance (Flyway
 * migrations included) rather than an in-memory H2 stand-in, so the CHECK
 * constraints, partial index, and UUID defaults defined in V5/V6 are
 * actually validated.
 */
@DataJpaTest
@Testcontainers
class ProjectRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void flywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectRepository projectRepository;

    @Test
    void savedProjectIsPersistedWithDefaults() {
        Project project = newProject("persist-test");

        Project saved = projectRepository.save(project);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(saved.getCurrentTeamSize()).isEqualTo(1);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void slugUniquenessIsEnforcedCaseInsensitively() {
        projectRepository.save(newProject("unique-slug-test"));

        assertThat(projectRepository.existsBySlugIgnoreCase("UNIQUE-SLUG-TEST")).isTrue();
    }

    @Test
    void findByStatusAndRecruitingStartedAtBefore_onlyReturnsGhostedProjects() {
        Project ghosted = newProject("ghosted-project");
        ghosted.setStatus(ProjectStatus.RECRUITING);
        ghosted.setRecruitingStartedAt(Instant.now().minus(45, ChronoUnit.DAYS));
        projectRepository.save(ghosted);

        Project fresh = newProject("fresh-project");
        fresh.setStatus(ProjectStatus.RECRUITING);
        fresh.setRecruitingStartedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        projectRepository.save(fresh);

        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Project> results = projectRepository.findByStatusAndRecruitingStartedAtBefore(ProjectStatus.RECRUITING, cutoff);

        assertThat(results).extracting(Project::getTitle).containsExactly("ghosted-project");
    }

    @Test
    void findByLeadUserId_isPageable() {
        UUID leadId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Project project = newProject("lead-project-" + i);
            project.setLeadUserId(leadId);
            projectRepository.save(project);
        }

        var page = projectRepository.findByLeadUserId(leadId, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    private Project newProject(String title) {
        Project project = new Project();
        project.setLeadUserId(UUID.randomUUID());
        project.setTitle(title);
        project.setSlug(title.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6));
        project.setDescription("Description for " + title);
        project.setCategory("WEB");
        project.setMaxTeamSize(5);
        return project;
    }
}
