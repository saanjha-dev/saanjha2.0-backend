package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ContributionLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContributionLedgerRepository extends JpaRepository<ContributionLedgerEntry, UUID> {

    Page<ContributionLedgerEntry> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    Page<ContributionLedgerEntry> findByProjectIdOrderByOccurredAtDesc(UUID projectId, Pageable pageable);

    List<ContributionLedgerEntry> findByUserId(UUID userId);

    boolean existsBySourceReferenceIdAndSourceTypeAndIsReversalFalse(UUID sourceReferenceId, String sourceType);

    Optional<ContributionLedgerEntry> findFirstBySourceReferenceIdAndSourceTypeAndIsReversalFalse(UUID sourceReferenceId, String sourceType);

    @Query("SELECT COUNT(e) FROM ContributionLedgerEntry e WHERE e.userId = :userId AND e.projectId = :projectId AND e.contributionType = 'LEADERSHIP'")
    long countLeadershipEntriesForProject(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    @Query("SELECT AVG(e.finalScore) FROM ContributionLedgerEntry e WHERE e.userId = :userId AND e.isReversal = false")
    Double averageScoreForUser(@Param("userId") UUID userId);

    long countByUserIdAndIntegrityFlagNot(UUID userId, com.saanjha.modules.contribution.entity.IntegrityFlag flag);
}
