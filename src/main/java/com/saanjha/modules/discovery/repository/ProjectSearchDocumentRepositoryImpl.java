package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.search.ProjectSearchFilters;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled native query rather than Specifications/QueryDSL: the
 * {@code @@} tsvector match and JSONB containment operators aren't
 * expressible through JPA Criteria, and this keeps the SQL fully
 * visible/auditable in one place (same tradeoff this codebase already
 * accepted for {@code PermissionCacheService}'s JdbcTemplate use).
 *
 * Uses JPA's numbered positional parameters ({@code ?1}, {@code ?2}, ...),
 * which Hibernate fills once per number regardless of how many times that
 * number appears in the SQL -- so the keyword placeholder can be reused in
 * both the WHERE clause and the {@code ts_rank} SELECT expression with a
 * single {@code setParameter} call.
 */
@Repository
@RequiredArgsConstructor
public class ProjectSearchDocumentRepositoryImpl implements ProjectSearchRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<ProjectSearchDocument> search(ProjectSearchFilters filters, Pageable pageable) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE is_indexed = TRUE ");
        int idx = 1;

        if (filters.status() != null && !filters.status().isBlank()) {
            where.append(" AND status = ?").append(idx++);
            params.add(filters.status());
        }
        if (filters.category() != null && !filters.category().isBlank()) {
            where.append(" AND category = ?").append(idx++);
            params.add(filters.category());
        }
        if (Boolean.TRUE.equals(filters.hasOpenSlots())) {
            where.append(" AND current_team_size < max_team_size ");
        }
        if (filters.requiredSkills() != null && !filters.requiredSkills().isEmpty()) {
            // JSONB array containment: every requested skill must appear in required_skills.
            where.append(" AND required_skills @> ?").append(idx++).append("::jsonb");
            params.add(toJsonArray(filters.requiredSkills()));
        }
        if (filters.tags() != null && !filters.tags().isEmpty()) {
            where.append(" AND tags @> ?").append(idx++).append("::jsonb");
            params.add(toJsonArray(filters.tags()));
        }

        boolean hasKeyword = filters.keyword() != null && !filters.keyword().isBlank();
        String rankSelect = "0::double precision AS rank_score";
        Integer keywordParamNumber = null;

        if (hasKeyword) {
            keywordParamNumber = idx++;
            int prefixParamNumber = idx++;
            where.append(" AND (search_vector @@ plainto_tsquery('english', ?").append(keywordParamNumber)
                    .append(") OR title ILIKE ?").append(prefixParamNumber).append(") ");
            rankSelect = "ts_rank(search_vector, plainto_tsquery('english', ?" + keywordParamNumber + ")) AS rank_score";
            params.add(filters.keyword());
            params.add(filters.keyword() + "%");
        }

        String baseSql = "SELECT *, " + rankSelect + " FROM dsc.dsc_project_documents" + where;
        String orderSql = hasKeyword
                ? " ORDER BY rank_score DESC, popularity_score DESC "
                : " ORDER BY popularity_score DESC, published_at DESC NULLS LAST ";
        String countSql = "SELECT count(*) FROM dsc.dsc_project_documents" + where;

        Query dataQuery = entityManager.createNativeQuery(baseSql + orderSql, ProjectSearchDocument.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindPositional(dataQuery, params);
        bindPositional(countQuery, params);

        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<ProjectSearchDocument> results = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProjectSearchDocument> findByAnyRequiredSkill(List<String> skills, int limit) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        // Built as an OR of per-skill jsonb containment checks rather than a Postgres
        // array/ANY() parameter -- keeps parameter binding identical to (and as provably
        // correct as) the containment approach already used in #search, without relying
        // on JDBC array-type binding this codebase has no existing precedent for.
        StringBuilder or = new StringBuilder();
        List<Object> params = new java.util.ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) or.append(" OR ");
            or.append("d.required_skills @> ?").append(i + 1).append("::jsonb");
            params.add("[\"" + skills.get(i).replace("\"", "\\\"") + "\"]");
        }

        String sql = "SELECT d.* FROM dsc.dsc_project_documents d "
                + "WHERE d.is_indexed = TRUE AND (" + or + ") "
                + "ORDER BY d.popularity_score DESC LIMIT ?" + (skills.size() + 1);

        Query query = entityManager.createNativeQuery(sql, ProjectSearchDocument.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        query.setParameter(skills.size() + 1, limit);
        return query.getResultList();
    }

    private void bindPositional(Query query, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}
