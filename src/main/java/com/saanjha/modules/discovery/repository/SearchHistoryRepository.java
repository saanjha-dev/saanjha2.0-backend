package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.SearchHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SearchHistoryRepository extends JpaRepository<SearchHistoryEntry, UUID> {

    List<SearchHistoryEntry> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SearchHistoryEntry s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
