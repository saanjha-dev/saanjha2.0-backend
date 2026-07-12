package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.SearchSuggestion;
import com.saanjha.modules.discovery.entity.SuggestionEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchSuggestionRepository extends JpaRepository<SearchSuggestion, UUID> {

    Optional<SearchSuggestion> findByTermAndEntityType(String term, SuggestionEntityType entityType);

    @Query(value = "SELECT * FROM dsc.dsc_search_suggestions WHERE term ILIKE :prefix "
            + "ORDER BY frequency DESC LIMIT :limit", nativeQuery = true)
    List<SearchSuggestion> findByPrefix(@Param("prefix") String prefix, @Param("limit") int limit);
}
