package com.saanjha.modules.discovery.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.entity.TechnologyStat;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import com.saanjha.modules.discovery.repository.TechnologyStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deliberately NOT event-incremental, unlike the other projection services.
 * A per-event increment/decrement scheme for "how many active projects
 * require skill X" would need to correctly undo itself on every possible
 * edit path (skill removed from a project's requirements, project archived,
 * developer skill removed) to avoid permanent drift — the same class of bug
 * risk this codebase's own review docs flag elsewhere (e.g. TD25/TD26:
 * narrow authorization/state-machine gaps introduced by an incremental fix
 * touching a shared surface). A full recompute over Discovery's own
 * {@code dsc} tables (not Project's/User's) is O(active projects +
 * developers), runs on the same schedule as the Trending Engine, and is
 * self-correcting by construction. See {@code TrendingScheduler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TechnologyProjectionService {

    private final ProjectSearchDocumentRepository projectRepository;
    private final DeveloperSearchDocumentRepository developerRepository;
    private final TechnologyStatRepository technologyStatRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recomputeAll() {
        Map<String, TechnologyStat> stats = new HashMap<>();

        for (ProjectSearchDocument project : projectRepository.findAll()) {
            if (!project.isIndexed()) {
                continue;
            }
            for (String skill : readStringArray(project.getRequiredSkillsJson())) {
                statFor(stats, skill).setProjectCount(statFor(stats, skill).getProjectCount() + 1);
            }
        }

        for (DeveloperSearchDocument developer : developerRepository.findAll()) {
            if (developer.isDeleted()) {
                continue;
            }
            for (Map<String, Object> skill : readSkillObjects(developer.getSkills())) {
                String name = String.valueOf(skill.get("skillName"));
                TechnologyStat stat = statFor(stats, name);
                stat.setDeveloperCount(stat.getDeveloperCount() + 1);
                if (Boolean.TRUE.equals(skill.get("isVerified"))) {
                    stat.setVerifiedDeveloperCount(stat.getVerifiedDeveloperCount() + 1);
                }
            }
        }

        Instant now = Instant.now();
        for (TechnologyStat stat : stats.values()) {
            // Trending score: weighted demand (project requirements) over supply
            // (developers who list it) plus a flat verified-talent bonus -- a simple,
            // pluggable-in-spirit scoring formula the Trending Engine treats as one
            // more input signal, not the final word (see TrendingEngineImpl).
            double supply = Math.max(1, stat.getDeveloperCount());
            stat.setTrendingScore((stat.getProjectCount() * 2.0 / supply) + (stat.getVerifiedDeveloperCount() * 0.1));
            stat.setLastComputedAt(now);
        }

        technologyStatRepository.saveAll(stats.values());
        log.info("Discovery: technology rollup recomputed for {} distinct technologies.", stats.size());
    }

    private TechnologyStat statFor(Map<String, TechnologyStat> stats, String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        return stats.computeIfAbsent(normalized, key -> technologyStatRepository.findById(key)
                .map(existing -> {
                    existing.setProjectCount(0);
                    existing.setDeveloperCount(0);
                    existing.setVerifiedDeveloperCount(0);
                    return existing;
                })
                .orElseGet(() -> {
                    TechnologyStat fresh = new TechnologyStat();
                    fresh.setTechnologyName(key);
                    return fresh;
                }));
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringArray(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSkillObjects(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
