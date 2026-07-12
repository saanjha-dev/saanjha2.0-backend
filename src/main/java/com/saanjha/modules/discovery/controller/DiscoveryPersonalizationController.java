package com.saanjha.modules.discovery.controller;

import com.saanjha.modules.discovery.dto.SavedSearchRequest;
import com.saanjha.modules.discovery.dto.SavedSearchResponse;
import com.saanjha.modules.discovery.dto.SearchHistoryResponse;
import com.saanjha.modules.discovery.search.DiscoveryPersonalizationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Personal surface: saved searches and search history. Every route requires authentication. */
@RestController
@RequestMapping("/v1/discovery/me")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('discovery:view')")
public class DiscoveryPersonalizationController {

    private final DiscoveryPersonalizationService service;

    @PostMapping("/saved-searches")
    @PreAuthorize("hasAuthority('discovery:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<SavedSearchResponse> saveSearch(@Valid @RequestBody SavedSearchRequest request) {
        return ApiEnvelope.success(service.saveSearch(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/saved-searches")
    public ApiEnvelope<List<SavedSearchResponse>> listSavedSearches() {
        return ApiEnvelope.success(service.listSavedSearches(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/saved-searches/{id}/run")
    public ApiEnvelope<SavedSearchResponse> markRun(@PathVariable UUID id) {
        return ApiEnvelope.success(service.markRun(SecurityUtils.getCurrentUserId(), id));
    }

    @DeleteMapping("/saved-searches/{id}")
    @PreAuthorize("hasAuthority('discovery:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedSearch(@PathVariable UUID id) {
        service.deleteSavedSearch(SecurityUtils.getCurrentUserId(), id);
    }

    @GetMapping("/history")
    public ApiEnvelope<List<SearchHistoryResponse>> getHistory(@RequestParam(defaultValue = "20") int limit) {
        return ApiEnvelope.success(service.getHistory(SecurityUtils.getCurrentUserId(), Math.min(limit, 100)));
    }

    @DeleteMapping("/history")
    @PreAuthorize("hasAuthority('discovery:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory() {
        service.clearHistory(SecurityUtils.getCurrentUserId());
    }
}
