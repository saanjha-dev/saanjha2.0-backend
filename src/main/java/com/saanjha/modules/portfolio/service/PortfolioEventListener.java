package com.saanjha.modules.portfolio.service;

import com.saanjha.modules.contribution.event.ContributionEvents.ContributionCorrectedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionMilestoneReachedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionRecordedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ReputationUpdatedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.team.event.TeamEvents.MemberJoinedEvent;
import com.saanjha.modules.team.event.TeamEvents.TeamArchivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Portfolio is primarily an event CONSUMER. Every listener here is thin —
 * translate the payload, delegate to the owning service — per this
 * codebase's own convention (see Team's/Contribution's listeners). AFTER_COMMIT
 * everywhere except {@link #onBadgeAwarded}, which reacts to Portfolio's own
 * just-published, not-yet-committed event within the same transaction (the
 * same pattern the codebase has no precedent for yet, so this uses a plain
 * {@code @EventListener}, not transactional, since it's an in-transaction
 * same-module echo, not a cross-module AFTER_COMMIT reaction).
 */
@Component
@RequiredArgsConstructor
public class PortfolioEventListener {

    private final PortfolioGenerationService generationService;
    private final PortfolioTimelineService timelineService;
    private final PortfolioBadgeEngine badgeEngine;

    // ========================================================================
    // PROJECT (gate)
    // ========================================================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCompleted(ProjectCompletedEvent event) {
        generationService.applyProjectCompletion(event.projectId(), event.leadUserId(), event.completedAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectArchived(ProjectArchivedEvent event) {
        generationService.discardPending(event.projectId());
    }

    // ========================================================================
    // TEAM (roster / role / tenure)
    // ========================================================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamArchived(TeamArchivedEvent event) {
        List<PortfolioGenerationService.RosterMember> roster = event.roster().stream()
                .map(m -> new PortfolioGenerationService.RosterMember(m.userId(), m.role(), m.contributionTitle(), m.joinedAt(), m.leftAt(), m.tenureDays()))
                .toList();
        generationService.applyTeamRoster(event.projectId(), roster);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoinedEvent event) {
        timelineService.recordJoinedProject(event.userId(), event.projectId(), "MEMBER", event.occurredAt());
    }

    // ========================================================================
    // CONTRIBUTION (score accumulation, reputation, milestones, corrections)
    // ========================================================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionRecorded(ContributionRecordedEvent event) {
        generationService.accumulateContribution(event.projectId(), event.userId(), event.finalScore(), event.contributionType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReputationUpdated(ReputationUpdatedEvent event) {
        generationService.applyReputationUpdate(event.userId(), event.reliabilityScore(), event.leadershipScore(),
                event.consistencyScore(), event.reviewQualityScore());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionMilestoneReached(ContributionMilestoneReachedEvent event) {
        if (!"TASKS_COMPLETED".equals(event.milestoneType())) {
            return; // Only tasksCompleted-based milestones map to a badge today; other milestone types are a documented future extension.
        }
        badgeEngine.awardMilestoneBadge(event.userId(), event.milestoneValue());
        timelineService.recordMilestone(event.userId(), event.milestoneValue(), event.occurredAt());
    }

    /** See {@code PortfolioGenerationService.applyContributionCorrection}'s Javadoc for why this is global-only. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionCorrected(ContributionCorrectedEvent event) {
        generationService.applyContributionCorrection(event.userId(), event.scoreDelta());
    }

    // ========================================================================
    // PORTFOLIO'S OWN EVENTS (timeline echo)
    // ========================================================================

    @EventListener
    public void onBadgeAwarded(BadgeAwardedEvent event) {
        timelineService.recordBadgeAwarded(event.userId(), event.badgeType(), event.awardedAt());
    }
}
