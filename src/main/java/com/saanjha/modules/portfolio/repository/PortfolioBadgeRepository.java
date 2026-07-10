package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.BadgeType;
import com.saanjha.modules.portfolio.entity.PortfolioBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioBadgeRepository extends JpaRepository<PortfolioBadge, UUID> {

    List<PortfolioBadge> findByUserIdOrderByAwardedAtDesc(UUID userId);

    boolean existsByUserIdAndBadgeType(UUID userId, BadgeType badgeType);
}
