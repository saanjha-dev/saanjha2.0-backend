package com.saanjha.modules.discovery.search;

import com.saanjha.modules.discovery.dto.SuggestionResponse;
import com.saanjha.modules.discovery.entity.SearchSuggestion;
import com.saanjha.modules.discovery.entity.SuggestionEntityType;
import com.saanjha.modules.discovery.repository.SearchSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Maintains the autocomplete source incrementally as documents are
 * projected (see call sites in {@code ProjectProjectionService}/
 * {@code DeveloperProjectionService}) rather than recomputing it from a
 * full scan -- the frequency counter only ever grows, so there is no
 * decrement/undo correctness problem the way there would be for
 * {@code TechnologyProjectionService}'s counts.
 */
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SearchSuggestionRepository repository;

    @Transactional
    public void recordTerm(String term, SuggestionEntityType type) {
        if (term == null || term.isBlank()) {
            return;
        }
        String normalized = term.trim();
        SearchSuggestion suggestion = repository.findByTermAndEntityType(normalized, type)
                .orElseGet(() -> {
                    SearchSuggestion fresh = new SearchSuggestion();
                    fresh.setTerm(normalized);
                    fresh.setEntityType(type);
                    fresh.setFrequency(0);
                    return fresh;
                });
        suggestion.setFrequency(suggestion.getFrequency() + 1);
        suggestion.setUpdatedAt(java.time.Instant.now());
        repository.save(suggestion);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> autocomplete(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return repository.findByPrefix(prefix.trim() + "%", limit).stream()
                .map(s -> new SuggestionResponse(s.getTerm(), s.getEntityType().name()))
                .toList();
    }
}
