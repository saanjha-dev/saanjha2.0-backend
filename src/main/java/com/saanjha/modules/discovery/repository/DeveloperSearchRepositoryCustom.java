package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.search.DeveloperSearchFilters;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DeveloperSearchRepositoryCustom {
    Page<DeveloperSearchDocument> search(DeveloperSearchFilters filters, Pageable pageable);

    /** Backs the teammate/similar-developer recommendation strategies -- "any of," not "all of." */
    java.util.List<DeveloperSearchDocument> findByAnySkill(java.util.List<String> skills, java.util.UUID excludeUserId, int limit);
}
