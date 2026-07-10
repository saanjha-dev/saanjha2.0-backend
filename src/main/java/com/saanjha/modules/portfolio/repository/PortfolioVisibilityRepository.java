package com.saanjha.modules.portfolio.repository;

import com.saanjha.modules.portfolio.entity.PortfolioVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioVisibilityRepository extends JpaRepository<PortfolioVisibility, UUID> {

    Optional<PortfolioVisibility> findByShareToken(String shareToken);
}
