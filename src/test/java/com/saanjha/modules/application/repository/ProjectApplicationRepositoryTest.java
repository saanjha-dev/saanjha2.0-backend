package com.saanjha.modules.application.repository;

import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.entity.ProjectApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V7 migration's partial unique index
 * (uq_app_active_application) against a real PostgreSQL instance — this
 * constraint is the last line of defense against duplicate applications
 * even if the application-layer check in ApplicationService were ever
 * bypassed by a race condition.
 */
@DataJpaTest
@Testcontainers
class ProjectApplicationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectApplicationRepository applicationRepository;

    @Test
    void partialUniqueIndex_blocksSecondActiveApplication_butAllowsAfterFirstIsTerminal() {
        UUID projectId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();

        applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.SUBMITTED));

        assertThatThrownBy(() -> {
            applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.UNDER_REVIEW));
            applicationRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void terminalStatusesDoNotCollideWithTheUniqueIndex() {
        UUID projectId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();

        applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.WITHDRAWN));
        applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.REJECTED));
        ProjectApplication third = applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.SUBMITTED));

        assertThat(third.getId()).isNotNull();
    }

    @Test
    void findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc_returnsMostRecent() {
        UUID projectId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();

        applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.WITHDRAWN));
        ProjectApplication mostRecent = applicationRepository.save(newApplication(projectId, applicantId, ApplicationStatus.REJECTED));

        var found = applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(projectId, applicantId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(mostRecent.getId());
    }

    private ProjectApplication newApplication(UUID projectId, UUID applicantId, ApplicationStatus status) {
        ProjectApplication application = new ProjectApplication();
        application.setProjectId(projectId);
        application.setApplicantId(applicantId);
        application.setMessage("Test application message.");
        application.setStatus(status);
        application.setExpiresAt(Instant.now().plus(21, ChronoUnit.DAYS));
        return application;
    }
}
