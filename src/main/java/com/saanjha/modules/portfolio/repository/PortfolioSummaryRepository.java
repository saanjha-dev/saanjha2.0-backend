package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PortfolioSummaryRepository extends JpaRepository<PortfolioSummary, UUID> {
}
