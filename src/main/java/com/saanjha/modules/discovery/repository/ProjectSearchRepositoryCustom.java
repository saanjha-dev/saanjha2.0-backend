package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.search.ProjectSearchFilters;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Full-text/filtered search over {@link ProjectSearchDocument}, backed by the
 * Postgres {@code tsvector}/GIN index maintained by the V22 migration
 * trigger. Kept out of the plain Spring Data repository because
 * keyword+prefix+multi-filter search doesn't express cleanly as a derived
 * query method.
 */
public interface ProjectSearchRepositoryCustom {
    Page<ProjectSearchDocument> search(ProjectSearchFilters filters, Pageable pageable);

    /**
     * Returns indexed, RECRUITING/IN_PROGRESS projects whose required skills
     * overlap with ANY of the given skills, ordered by overlap size then
     * popularity. Backs the skill-overlap recommendation strategy -- kept
     * distinct from {@link #search} because "any of" (recommendation) and
     * "all of" (a Lead deliberately filtering for a specific stack) are
     * different, both legitimate, query semantics.
     */
    java.util.List<ProjectSearchDocument> findByAnyRequiredSkill(java.util.List<String> skills, int limit);
}
