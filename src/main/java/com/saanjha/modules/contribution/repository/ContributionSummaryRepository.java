package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ContributionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContributionSummaryRepository extends JpaRepository<ContributionSummary, UUID> {

    @Query("SELECT s FROM ContributionSummary s ORDER BY s.totalScore DESC")
    List<ContributionSummary> findTopContributors(org.springframework.data.domain.Pageable pageable);

    /** Powers the monthly snapshot scheduler — every user with any recorded contribution gets a snapshot. */
    @Query("SELECT s.userId FROM ContributionSummary s")
    List<UUID> findAllUserIds();
}
