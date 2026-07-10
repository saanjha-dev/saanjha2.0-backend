package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioGenerationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioGenerationStateRepository extends JpaRepository<PortfolioGenerationState, PortfolioGenerationState.Key> {

    /**
     * Pessimistic write lock closes the race window between "read state" and
     * "write state" for the same (project, user) pair when a
     * {@code ContributionRecordedEvent} and, say, {@code TeamArchivedEvent}'s
     * processing for that same pair could plausibly overlap under concurrent
     * transaction commit timing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PortfolioGenerationState> findWithLockByProjectIdAndUserId(UUID projectId, UUID userId);

    List<PortfolioGenerationState> findByProjectIdAndGeneratedFalse(UUID projectId);

    List<PortfolioGenerationState> findByProjectId(UUID projectId);
}
