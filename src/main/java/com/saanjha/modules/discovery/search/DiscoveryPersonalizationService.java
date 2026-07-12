package com.saanjha.modules.discovery.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.dto.SavedSearchRequest;
import com.saanjha.modules.discovery.dto.SavedSearchResponse;
import com.saanjha.modules.discovery.dto.SearchHistoryResponse;
import com.saanjha.modules.discovery.entity.SavedSearch;
import com.saanjha.modules.discovery.repository.SavedSearchRepository;
import com.saanjha.modules.discovery.repository.SearchHistoryRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the personal, per-user surface: saved searches and search history. */
@Service
@RequiredArgsConstructor
public class DiscoveryPersonalizationService {

    private final SavedSearchRepository savedSearchRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SavedSearchResponse saveSearch(UUID userId, SavedSearchRequest request) {
        if (savedSearchRepository.existsByUserIdAndName(userId, request.name())) {
            throw new AppException(ErrorCode.CONFLICT, "A saved search with this name already exists.");
        }
        SavedSearch entity = new SavedSearch();
        entity.setUserId(userId);
        entity.setName(request.name());
        entity.setQueryText(request.queryText());
        entity.setFilters(toJson(request.filters()));
        return toResponse(savedSearchRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> listSavedSearches(UUID userId) {
        return savedSearchRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteSavedSearch(UUID userId, UUID savedSearchId) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(savedSearchId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Saved search not found."));
        savedSearchRepository.delete(entity);
    }

    @Transactional
    public SavedSearchResponse markRun(UUID userId, UUID savedSearchId) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(savedSearchId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Saved search not found."));
        entity.setLastRunAt(Instant.now());
        return toResponse(savedSearchRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getHistory(UUID userId, int limit) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, limit)).stream()
                .map(h -> new SearchHistoryResponse(h.getId(), h.getQueryText(), h.getResultCount(), h.getSearchedAt()))
                .toList();
    }

    @Transactional
    public void clearHistory(UUID userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    private SavedSearchResponse toResponse(SavedSearch entity) {
        return new SavedSearchResponse(entity.getId(), entity.getName(), entity.getQueryText(),
                fromJson(entity.getFilters()), entity.getLastRunAt(), entity.getCreatedAt());
    }

    private String toJson(Map<String, Object> filters) {
        try {
            return objectMapper.writeValueAsString(filters == null ? Map.of() : filters);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
