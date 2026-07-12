package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.TechnologyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TechnologyStatRepository extends JpaRepository<TechnologyStat, String> {
    List<TechnologyStat> findAllByOrderByTrendingScoreDesc(Pageable pageable);
}
