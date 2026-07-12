package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.TrendingEntityType;
import com.saanjha.modules.discovery.entity.TrendingSnapshot;
import com.saanjha.modules.discovery.entity.TrendingWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TrendingSnapshotRepository extends JpaRepository<TrendingSnapshot, UUID> {

    List<TrendingSnapshot> findByEntityTypeAndWindowOrderByRankAsc(TrendingEntityType entityType, TrendingWindow window);

    @Modifying
    @Query("DELETE FROM TrendingSnapshot t WHERE t.entityType = :entityType AND t.window = :window")
    void deleteByEntityTypeAndWindow(@Param("entityType") TrendingEntityType entityType,
                                      @Param("window") TrendingWindow window);
}
