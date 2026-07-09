package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ContributionType;
import com.saanjha.modules.contribution.entity.ScoringWeights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoringWeightsRepository extends JpaRepository<ScoringWeights, UUID> {

    Optional<ScoringWeights> findByContributionTypeAndActiveTrue(ContributionType contributionType);

    List<ScoringWeights> findByActiveTrue();

    List<ScoringWeights> findByVersion(int version);

    int countByContributionType(ContributionType contributionType);
}
