package com.saanjha.modules.discovery.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionRecordedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ReputationUpdatedEvent;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.entity.SuggestionEntityType;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.search.SuggestionService;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioVisibilityChangedEvent;
import com.saanjha.modules.user.event.UserEvents.UserDiscoveryUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds and maintains {@link DeveloperSearchDocument} from User's own
 * read-model sync event, enriched incrementally by Contribution and
 * Portfolio events. Never reads {@code usr.*} tables directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeveloperProjectionService {

    private final DeveloperSearchDocumentRepository repository;
    private final ObjectMapper objectMapper;
    private final SuggestionService suggestionService;

    public void applyDiscoverySync(UserDiscoveryUpdatedEvent event) {
        DeveloperSearchDocument doc = repository.findById(event.userId())
                .orElseGet(DeveloperSearchDocument::new);

        doc.setUserId(event.userId());
        doc.setDisplayName(event.displayName());
        doc.setUniqueHandle(event.uniqueHandle());
        doc.setHeadline(event.headline());
        doc.setBioExcerpt(event.bioExcerpt());
        doc.setLocation(event.location());
        doc.setExperienceLevel(event.experienceLevel());
        doc.setSkills(toJson(event.skills()));
        doc.setInterests(toJson(event.interests()));
        doc.setProfileScore(event.profileScore());
        doc.setProjectsCompleted(event.projectsCompleted());
        doc.setDeleted(event.isDeleted());

        repository.save(doc);

        if (!event.isDeleted()) {
            if (event.uniqueHandle() != null) {
                suggestionService.recordTerm(event.uniqueHandle(), SuggestionEntityType.DEVELOPER_HANDLE);
            }
            event.skills().forEach(skill -> suggestionService.recordTerm(skill.skillName(), SuggestionEntityType.SKILL));
        }
    }

    /**
     * Ordering assumption, documented rather than defended against with a
     * retry queue: a profile's first {@code UserDiscoveryUpdatedEvent} is
     * expected to exist before any reputation/contribution activity can
     * occur for that user (you can't earn a contribution score before you
     * have a profile). If it doesn't, the update is skipped and logged
     * rather than creating a half-populated document.
     */
    public void applyReputationUpdated(ReputationUpdatedEvent event) {
        repository.findById(event.userId()).ifPresentOrElse(doc -> {
            doc.setReliabilityScore(event.reliabilityScore());
            doc.setLeadershipScore(event.leadershipScore());
            doc.setConsistencyScore(event.consistencyScore());
            doc.setReviewQualityScore(event.reviewQualityScore());
            repository.save(doc);
        }, () -> log.debug("Discovery: ReputationUpdatedEvent for {} arrived before any developer document existed.",
                event.userId()));
    }

    public void applyContributionRecorded(ContributionRecordedEvent event) {
        repository.findById(event.userId()).ifPresentOrElse(doc -> {
            doc.setContributionTotalScore(doc.getContributionTotalScore() + event.finalScore());
            repository.save(doc);
        }, () -> log.debug("Discovery: ContributionRecordedEvent for {} arrived before any developer document existed.",
                event.userId()));
    }

    public void applyBadgeAwarded(BadgeAwardedEvent event) {
        repository.findById(event.userId()).ifPresent(doc -> {
            doc.setPortfolioBadgeCount(doc.getPortfolioBadgeCount() + 1);
            repository.save(doc);
        });
    }

    public void applyPortfolioVisibilityChanged(PortfolioVisibilityChangedEvent event) {
        repository.findById(event.userId()).ifPresent(doc -> {
            doc.setPortfolioVisibility(event.visibility());
            repository.save(doc);
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Discovery: failed to serialize projection field, defaulting to empty array.", e);
            return "[]";
        }
    }
}
