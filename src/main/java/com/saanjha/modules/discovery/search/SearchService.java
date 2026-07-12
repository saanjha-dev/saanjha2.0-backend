package com.saanjha.modules.discovery.search;

import com.saanjha.modules.discovery.dto.DeveloperSummaryResponse;
import com.saanjha.modules.discovery.dto.ProjectSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SearchService {
    Page<ProjectSummaryResponse> searchProjects(ProjectSearchFilters filters, Pageable pageable, UUID requesterId);
    Page<DeveloperSummaryResponse> searchDevelopers(DeveloperSearchFilters filters, Pageable pageable, UUID requesterId);
}
