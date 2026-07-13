package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.TrustRiskLevel;
import com.saanjha.modules.admin.entity.TrustScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustScoreRepository extends JpaRepository<TrustScore, UUID> {

    Optional<TrustScore> findByUserId(UUID userId);

    long countByRiskLevel(TrustRiskLevel riskLevel);
}
