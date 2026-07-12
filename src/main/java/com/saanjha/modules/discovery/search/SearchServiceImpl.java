package com.saanjha.modules.discovery.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.dto.DeveloperSummaryResponse;
import com.saanjha.modules.discovery.dto.ProjectSummaryResponse;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.discovery.entity.SearchHistoryEntry;
import com.saanjha.modules.discovery.ranking.RankingContextFactory;
import com.saanjha.modules.discovery.ranking.RankingEngine;
import com.saanjha.modules.discovery.ranking.RankingScore;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combines the tsvector-backed repository query (relevance/filter matching)
 * with the {@link RankingEngine} (quality/freshness/activity signals) to
 * produce the final ordered response.
 *
 * Known, documented simplification: the native search query's per-row
 * {@code ts_rank} value isn't retrievable once Hibernate maps the result set
 * onto the {@code ProjectSearchDocument}/{@code DeveloperSearchDocument}
 * entity (extra projected columns are dropped by that mapping mode). The
 * database query still uses relevance for its own ORDER BY/pagination; the
 * {@link RankingEngine} breakdown returned to the caller scores search
 * relevance as 0 rather than re-deriving it. Fixing this cleanly needs a
 * {@code SqlResultSetMapping}/constructor-projection change, which is a
 * reasonable follow-up but out of scope for this pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ProjectSearchDocumentRepository projectRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final RankingEngine rankingEngine;
    private final RankingContextFactory rankingContextFactory;
    private final ObjectMapper objectMapper;
    private final DiscoveryMetrics metrics;

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> searchProjects(ProjectSearchFilters filters, Pageable pageable, UUID requesterId) {
        long start = System.currentTimeMillis();
        metrics.incrementSearchQuery("PROJECT", filters.keyword() != null && !filters.keyword().isBlank());
        Page<ProjectSearchDocument> page = projectRepository.search(filters, pageable);

        Page<ProjectSummaryResponse> result = page.map(doc -> {
            RankingScore ranking = rankingEngine.rank(rankingContextFactory.forProject(doc, 0));
            return new ProjectSummaryResponse(
                    doc.getProjectId(), doc.getTitle(), doc.getSlug(), doc.getCategory(), doc.getVisibility(),
                    doc.getStatus(), readStringList(doc.getRequiredSkillsJson()), readStringList(doc.getTagsJson()),
                    doc.getMaxTeamSize(), doc.getCurrentTeamSize(), ranking.total(), ranking.breakdown());
        });

        recordHistory(requesterId, filters.keyword(), filters, null, (int) page.getTotalElements());
        metrics.recordSearchLatency("PROJECT", System.currentTimeMillis() - start);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeveloperSummaryResponse> searchDevelopers(DeveloperSearchFilters filters, Pageable pageable, UUID requesterId) {
        long start = System.currentTimeMillis();
        metrics.incrementSearchQuery("DEVELOPER", filters.keyword() != null && !filters.keyword().isBlank());
        Page<DeveloperSearchDocument> page = developerRepository.search(filters, pageable);

        Page<DeveloperSummaryResponse> result = page.map(doc -> {
            RankingScore ranking = rankingEngine.rank(rankingContextFactory.forDeveloper(doc, 0));
            return new DeveloperSummaryResponse(
                    doc.getUserId(), doc.getDisplayName(), doc.getUniqueHandle(), doc.getHeadline(),
                    doc.getLocation(), doc.getExperienceLevel(), readSkillList(doc.getSkills()),
                    doc.getProfileScore(), ranking.total(), ranking.breakdown());
        });

        recordHistory(requesterId, filters.keyword(), null, filters, (int) page.getTotalElements());
        metrics.recordSearchLatency("DEVELOPER", System.currentTimeMillis() - start);
        return result;
    }

    private void recordHistory(UUID requesterId, String keyword, ProjectSearchFilters projectFilters,
                                DeveloperSearchFilters developerFilters, int resultCount) {
        if (requesterId == null) {
            return; // Never logged for anonymous callers -- see SearchHistoryEntry's Javadoc.
        }
        try {
            SearchHistoryEntry entry = new SearchHistoryEntry();
            entry.setUserId(requesterId);
            entry.setQueryText(keyword);
            entry.setResultCount(resultCount);
            entry.setFilters(objectMapper.writeValueAsString(
                    projectFilters != null ? projectFilters : developerFilters));
            searchHistoryRepository.save(entry);
        } catch (Exception e) {
            log.warn("Discovery: failed to record search history for user {}.", requesterId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSkillList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
