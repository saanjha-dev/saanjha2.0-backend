package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ContributionLedgerEntry;
import com.saanjha.modules.contribution.entity.ContributionType;
import com.saanjha.modules.contribution.entity.IntegrityFlag;
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
 * Exercises the V15 migration's partial unique index: at most one
 * non-reversal ledger entry per (sourceReferenceId, sourceType) — the DB-
 * level idempotency guard against duplicate event delivery. Reversal rows
 * are explicitly exempt and must be allowed to reference the same source.
 */
@DataJpaTest
@Testcontainers
class ContributionLedgerRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private ContributionLedgerRepository ledgerRepository;

    @Test
    void duplicateNonReversalEntryForSameSource_isRejected() {
        UUID taskId = UUID.randomUUID();
        ledgerRepository.save(newEntry(taskId));

        assertThatThrownBy(() -> {
            ledgerRepository.save(newEntry(taskId));
            ledgerRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reversalEntryForSameSource_isAllowedAlongsideTheOriginal() {
        UUID taskId = UUID.randomUUID();
        ContributionLedgerEntry original = ledgerRepository.save(newEntry(taskId));
        ContributionLedgerEntry reversal = ContributionLedgerEntry.reversalOf(original, "correction");

        ContributionLedgerEntry saved = ledgerRepository.save(reversal);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isReversal()).isTrue();
        assertThat(saved.getFinalScore()).isEqualTo(-original.getFinalScore());
    }

    @Test
    void differentSourceType_forSameTaskId_isAllowed() {
        UUID taskId = UUID.randomUUID();
        ledgerRepository.save(newEntry(taskId)); // TASK_COMPLETED

        ContributionLedgerEntry review = ContributionLedgerEntry.create(
                UUID.randomUUID(), UUID.randomUUID(), "TASK_REVIEWED", taskId, ContributionType.TASK_REVIEW,
                null, 6.0, 1.0, 1.0, 1.0, "[]", IntegrityFlag.NONE, 1, Instant.now());
        ContributionLedgerEntry saved = ledgerRepository.save(review);

        assertThat(saved.getId()).isNotNull();
    }

    private ContributionLedgerEntry newEntry(UUID taskId) {
        return ContributionLedgerEntry.create(
                UUID.randomUUID(), UUID.randomUUID(), "TASK_COMPLETED", taskId, ContributionType.TASK_COMPLETION,
                "FEATURE", 10.0, 1.0, 1.0, 1.0, "[]", IntegrityFlag.NONE, 1, Instant.now());
    }
}
