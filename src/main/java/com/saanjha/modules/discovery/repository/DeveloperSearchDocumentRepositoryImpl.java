package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.search.DeveloperSearchFilters;
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

/** Same rationale/approach as {@link ProjectSearchDocumentRepositoryImpl} — see its Javadoc. */
@Repository
@RequiredArgsConstructor
public class DeveloperSearchDocumentRepositoryImpl implements DeveloperSearchRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<DeveloperSearchDocument> search(DeveloperSearchFilters filters, Pageable pageable) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE is_deleted = FALSE ");
        int idx = 1;

        if (filters.experienceLevel() != null && !filters.experienceLevel().isBlank()) {
            where.append(" AND experience_level = ?").append(idx++);
            params.add(filters.experienceLevel());
        }
        if (filters.minProfileScore() != null) {
            where.append(" AND profile_score >= ?").append(idx++);
            params.add(filters.minProfileScore());
        }
        if (filters.skills() != null && !filters.skills().isEmpty()) {
            // JSONB array containment either way -- consistent with the approach used
            // elsewhere in this class, and avoids binding a raw SQL array parameter.
            // Every requested skill must appear (verified, if requested) among the
            // developer's skills.
            where.append(" AND skills @> ?").append(idx++).append("::jsonb");
            params.add(Boolean.TRUE.equals(filters.verifiedSkillsOnly())
                    ? toSkillsJsonArray(filters.skills(), true)
                    : toSkillsJsonArray(filters.skills(), false));
        }
        // Extension-point filters: no upstream event populates these columns today (see
        // DeveloperSearchDocument's Javadoc), so applying either deliberately yields zero
        // rows rather than silently ignoring a filter the caller explicitly asked for.
        if (filters.availabilityStatus() != null && !filters.availabilityStatus().isBlank()) {
            where.append(" AND availability_status = ?").append(idx++);
            params.add(filters.availabilityStatus());
        }
        if (filters.remotePreference() != null && !filters.remotePreference().isBlank()) {
            where.append(" AND remote_preference = ?").append(idx++);
            params.add(filters.remotePreference());
        }

        boolean hasKeyword = filters.keyword() != null && !filters.keyword().isBlank();
        String rankSelect = "0::double precision AS rank_score";
        if (hasKeyword) {
            int keywordParamNumber = idx++;
            int prefixParamNumber = idx++;
            where.append(" AND (search_vector @@ plainto_tsquery('english', ?").append(keywordParamNumber)
                    .append(") OR display_name ILIKE ?").append(prefixParamNumber)
                    .append(" OR unique_handle ILIKE ?").append(prefixParamNumber).append(") ");
            rankSelect = "ts_rank(search_vector, plainto_tsquery('english', ?" + keywordParamNumber + ")) AS rank_score";
            params.add(filters.keyword());
            params.add(filters.keyword() + "%");
        }

        String baseSql = "SELECT *, " + rankSelect + " FROM dsc.dsc_developer_documents" + where;
        String orderSql = hasKeyword
                ? " ORDER BY rank_score DESC, profile_score DESC "
                : " ORDER BY profile_score DESC, contribution_total_score DESC ";
        String countSql = "SELECT count(*) FROM dsc.dsc_developer_documents" + where;

        Query dataQuery = entityManager.createNativeQuery(baseSql + orderSql, DeveloperSearchDocument.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindPositional(dataQuery, params);
        bindPositional(countQuery, params);

        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<DeveloperSearchDocument> results = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DeveloperSearchDocument> findByAnySkill(List<String> skills, java.util.UUID excludeUserId, int limit) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        StringBuilder or = new StringBuilder();
        List<Object> params = new ArrayList<>();
        int idx = 1;
        for (String skill : skills) {
            if (idx > 1) or.append(" OR ");
            or.append("d.skills @> ?").append(idx++).append("::jsonb");
            params.add("[{\"skillName\":\"" + skill.replace("\"", "\\\"") + "\"}]");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT d.* FROM dsc.dsc_developer_documents d WHERE d.is_deleted = FALSE AND (" + or + ") ");
        if (excludeUserId != null) {
            sql.append(" AND d.user_id <> ?").append(idx++);
            params.add(excludeUserId);
        }
        sql.append(" ORDER BY d.profile_score DESC LIMIT ?").append(idx);

        Query query = entityManager.createNativeQuery(sql.toString(), DeveloperSearchDocument.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        query.setParameter(idx, limit);
        return query.getResultList();
    }

    private void bindPositional(Query query, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
    }

    private String toSkillsJsonArray(List<String> skills, boolean requireVerified) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"skillName\":\"").append(skills.get(i).replace("\"", "\\\"")).append("\"");
            if (requireVerified) {
                sb.append(",\"isVerified\":true");
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }
}
