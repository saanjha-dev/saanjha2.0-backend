package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V17 migration's unique (user_id, project_id) index — a
 * user can have exactly one verified entry per project, enforced at the DB
 * level so a raced double-generation attempt (e.g. two overlapping
 * tryGenerate calls under a lost lock) fails loudly instead of silently
 * duplicating history.
 */
@DataJpaTest
@Testcontainers
class PortfolioEntryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private PortfolioEntryRepository entryRepository;

    @Test
    void duplicateEntryForSameUserAndProject_isRejected() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        entryRepository.save(newEntry(userId, projectId));

        assertThatThrownBy(() -> {
            entryRepository.save(newEntry(userId, projectId));
            entryRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUserDifferentProjects_isAllowed() {
        UUID userId = UUID.randomUUID();
        entryRepository.save(newEntry(userId, UUID.randomUUID()));
        entryRepository.save(newEntry(userId, UUID.randomUUID()));
        entryRepository.flush();

        assertThat(entryRepository.findByUserIdOrderByCompletedAtDesc(userId)).hasSize(2);
    }

    @Test
    void existsByUserIdAndWasLeadTrue_reflectsLeadershipEntriesOnly() {
        UUID userId = UUID.randomUUID();
        entryRepository.save(newEntry(userId, UUID.randomUUID()));

        assertThat(entryRepository.existsByUserIdAndWasLeadTrue(userId)).isFalse();
    }

    private PortfolioEntry newEntry(UUID userId, UUID projectId) {
        return PortfolioEntry.create(
                userId, projectId,
                "Test Project", "test-project-" + projectId, "WEB", "A test project", "[\"java\"]",
                "MEMBER", false, "Built the backend",
                Instant.now().minusSeconds(1000), Instant.now(), 10L,
                42.0, 5, 2,
                Instant.now());
    }
}
