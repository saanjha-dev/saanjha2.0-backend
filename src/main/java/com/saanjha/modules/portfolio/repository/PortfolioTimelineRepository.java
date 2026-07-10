package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioTimelineEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PortfolioTimelineRepository extends JpaRepository<PortfolioTimelineEntry, UUID> {

    Page<PortfolioTimelineEntry> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
}
