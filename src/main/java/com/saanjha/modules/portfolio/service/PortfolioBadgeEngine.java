package com.saanjha.modules.portfolio.service;

import com.saanjha.modules.portfolio.entity.BadgeType;
import com.saanjha.modules.portfolio.entity.PortfolioBadge;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.portfolio.repository.PortfolioBadgeRepository;
import com.saanjha.modules.project.service.ProjectSnapshotProvider.ProjectSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * All award rules in one place, deliberately — a badge system whose rules
 * are scattered across multiple listener methods becomes impossible to
 * audit ("why did this user get this badge?"). Every method here is
 * idempotent via the DB's own unique (user_id, badge_type) constraint —
 * {@code awardIfAbsent} catches the constraint violation as the concurrency
 * guard, the same defensive posture {@code ContributionLedgerEntry}'s own
 * unique index takes, rather than a check-then-act race.
 */
@Component
@RequiredArgsConstructor
public class PortfolioBadgeEngine {

    /** Free-form {@code ProjectTag} keyword heuristics — an honest approximation, not a real tech-stack detector. See BadgeType's Javadoc for what's deliberately NOT included. */
    private static final Set<String> BACKEND_TAG_KEYWORDS = Set.of(
            "spring", "spring-boot", "java", "backend", "postgres", "postgresql", "node", "nodejs", "django", "flask", "api");
    private static final Set<String> FRONTEND_TAG_KEYWORDS = Set.of(
            "react", "frontend", "vue", "angular", "css", "tailwind", "ui", "ux", "nextjs", "next.js");
    private static final Set<String> OPEN_SOURCE_TAG_KEYWORDS = Set.of("open-source", "opensource", "oss");

    private static final int BACKEND_SPECIALIST_THRESHOLD = 3;
    private static final int FRONTEND_SPECIALIST_THRESHOLD = 3;

    private final PortfolioBadgeRepository badgeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Called once per newly-generated entry. Evaluates every entry-triggered rule; milestone-triggered rules are handled separately in {@link #awardMilestoneBadge}. */
    public void evaluateOnEntryGenerated(UUID userId, boolean wasLead, boolean firstLeadEntry, ProjectSnapshot snapshot,
                                         long backendTaggedCompletedCount, long frontendTaggedCompletedCount) {
        if (wasLead && firstLeadEntry) {
            awardIfAbsent(userId, BadgeType.PROJECT_LEADER, "{}");
        }
        if (snapshot != null && matchesAny(snapshot.technologyTags(), OPEN_SOURCE_TAG_KEYWORDS)) {
            awardIfAbsent(userId, BadgeType.OPEN_SOURCE_CONTRIBUTOR, "{\"projectId\":\"" + snapshot.projectId() + "\"}");
        }
        if (backendTaggedCompletedCount >= BACKEND_SPECIALIST_THRESHOLD) {
            awardIfAbsent(userId, BadgeType.BACKEND_SPECIALIST, "{\"completedCount\":" + backendTaggedCompletedCount + "}");
        }
        if (frontendTaggedCompletedCount >= FRONTEND_SPECIALIST_THRESHOLD) {
            awardIfAbsent(userId, BadgeType.FRONTEND_SPECIALIST, "{\"completedCount\":" + frontendTaggedCompletedCount + "}");
        }
    }

    /** Directly relays Contribution's own milestone thresholds — never recomputes a task count. */
    public void awardMilestoneBadge(UUID userId, int milestoneValue) {
        BadgeType type = milestoneToBadgeType(milestoneValue);
        if (type != null) {
            awardIfAbsent(userId, type, "{\"milestoneValue\":" + milestoneValue + "}");
        }
    }

    public boolean projectHasBackendTags(ProjectSnapshot snapshot) {
        return snapshot != null && matchesAny(snapshot.technologyTags(), BACKEND_TAG_KEYWORDS);
    }

    public boolean projectHasFrontendTags(ProjectSnapshot snapshot) {
        return snapshot != null && matchesAny(snapshot.technologyTags(), FRONTEND_TAG_KEYWORDS);
    }

    private BadgeType milestoneToBadgeType(int milestoneValue) {
        return switch (milestoneValue) {
            case 10 -> BadgeType.TASKS_COMPLETED_10;
            case 25 -> BadgeType.TASKS_COMPLETED_25;
            case 50 -> BadgeType.TASKS_COMPLETED_50;
            case 100 -> BadgeType.TASKS_COMPLETED_100;
            case 250 -> BadgeType.TASKS_COMPLETED_250;
            case 500 -> BadgeType.TASKS_COMPLETED_500;
            case 1000 -> BadgeType.TASKS_COMPLETED_1000;
            default -> null;
        };
    }

    private boolean matchesAny(List<String> tags, Set<String> keywords) {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(tag -> keywords.contains(tag.toLowerCase(Locale.ROOT)));
    }

    private void awardIfAbsent(UUID userId, BadgeType badgeType, String evidenceJson) {
        if (badgeRepository.existsByUserIdAndBadgeType(userId, badgeType)) {
            return;
        }
        try {
            Instant now = Instant.now();
            badgeRepository.save(PortfolioBadge.create(userId, badgeType, evidenceJson, now));
            eventPublisher.publishEvent(new BadgeAwardedEvent(userId, badgeType.name(), now));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Lost the race to a concurrent award for the same (user, badgeType) — already awarded, safe no-op.
        }
    }
}
