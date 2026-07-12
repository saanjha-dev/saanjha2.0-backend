package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {
    List<SavedSearch> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<SavedSearch> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndName(UUID userId, String name);
}
