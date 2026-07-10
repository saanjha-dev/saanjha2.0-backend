package com.saanjha.modules.portfolio.service;

import com.saanjha.modules.portfolio.entity.PortfolioTimelineEntry;
import com.saanjha.modules.portfolio.entity.TimelineEventType;
import com.saanjha.modules.portfolio.repository.PortfolioTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Kept separate from {@code PortfolioGenerationService} because timeline
 * rows are written from several independent listener paths (project
 * completion, team joins, milestones, badges) — centralizing the
 * "how do we phrase this" logic here keeps that phrasing consistent and
 * makes it trivial to extend with a new event-driven timeline entry type
 * later without touching the generation orchestration itself.
 */
@Service
@RequiredArgsConstructor
public class PortfolioTimelineService {

    private final PortfolioTimelineRepository timelineRepository;

    @Transactional
    public void recordJoinedProject(UUID userId, UUID projectId, String role, Instant occurredAt) {
        String description = "MEMBER".equalsIgnoreCase(role)
                ? "Joined a project as a team member"
                : "Joined a project";
        timelineRepository.save(PortfolioTimelineEntry.create(userId, projectId, TimelineEventType.JOINED_PROJECT, description, occurredAt));
    }

    @Transactional
    public void recordProjectCompleted(UUID userId, UUID projectId, String projectTitle, boolean wasLead, Instant occurredAt) {
        timelineRepository.save(PortfolioTimelineEntry.create(
                userId, projectId, TimelineEventType.PROJECT_COMPLETED,
                "Completed \"" + projectTitle + "\"", occurredAt));
        if (wasLead) {
            timelineRepository.save(PortfolioTimelineEntry.create(
                    userId, projectId, TimelineEventType.LED_TEAM,
                    "Led the team on \"" + projectTitle + "\"", occurredAt));
        }
    }

    @Transactional
    public void recordMilestone(UUID userId, int milestoneValue, Instant occurredAt) {
        timelineRepository.save(PortfolioTimelineEntry.create(
                userId, null, TimelineEventType.MILESTONE_REACHED,
                "Reached " + milestoneValue + " tasks completed", occurredAt));
    }

    @Transactional
    public void recordBadgeAwarded(UUID userId, String badgeType, Instant occurredAt) {
        timelineRepository.save(PortfolioTimelineEntry.create(
                userId, null, TimelineEventType.BADGE_AWARDED,
                "Earned the " + badgeType.replace('_', ' ').toLowerCase() + " badge", occurredAt));
    }
}
