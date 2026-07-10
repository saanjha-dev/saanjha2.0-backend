package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioEntryRepository extends JpaRepository<PortfolioEntry, UUID> {

    Page<PortfolioEntry> findByUserIdOrderByCompletedAtDesc(UUID userId, Pageable pageable);

    List<PortfolioEntry> findByUserIdOrderByCompletedAtDesc(UUID userId);

    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);

    Optional<PortfolioEntry> findByUserIdAndProjectId(UUID userId, UUID projectId);

    /** Backs {@code PortfolioBadgeEngine}'s PROJECT_LEADER check without a second query round-trip per candidate. */
    boolean existsByUserIdAndWasLeadTrue(UUID userId);
}
