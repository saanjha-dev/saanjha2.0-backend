package com.saanjha.modules.discovery.event;

import com.saanjha.modules.contribution.event.ContributionEvents.ContributionRecordedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ReputationUpdatedEvent;
import com.saanjha.modules.discovery.projection.DeveloperProjectionService;
import com.saanjha.modules.discovery.projection.ProjectProjectionService;
import com.saanjha.modules.discovery.projection.TeamProjectionService;
import com.saanjha.modules.portfolio.event.PortfolioEvents.BadgeAwardedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioVisibilityChangedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectDiscoveryUpdatedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectPublishedEvent;
import com.saanjha.modules.team.event.TeamEvents.*;
import com.saanjha.modules.user.event.UserEvents.UserDiscoveryUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Discovery's single event-intake surface. Every listener uses
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — the mandatory
 * pattern for this codebase (ADR-001) — so a projection is never built from
 * data another module's transaction ends up rolling back.
 *
 * Deliberately one class per the convention already established by
 * {@code PortfolioEventListener}/{@code TeamEventListener}: one obvious place
 * to see everything Discovery reacts to, rather than scattering listener
 * methods across the projection services themselves (those stay pure,
 * event-agnostic, and independently unit-testable).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryEventListener {

    private final ProjectProjectionService projectProjectionService;
    private final DeveloperProjectionService developerProjectionService;
    private final TeamProjectionService teamProjectionService;

    // ------------------------------------------------------------------
    // Project
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectDiscoveryUpdated(ProjectDiscoveryUpdatedEvent event) {
        log.info("DIAG: onProjectDiscoveryUpdated received for project {}", event.projectId());
        projectProjectionService.applyDiscoverySync(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectPublished(ProjectPublishedEvent event) {
        projectProjectionService.applyPublished(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectArchived(ProjectArchivedEvent event) {
        projectProjectionService.applyArchived(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCompleted(ProjectCompletedEvent event) {
        projectProjectionService.applyCompleted(event);
    }

    // ------------------------------------------------------------------
    // User
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDiscoveryUpdated(UserDiscoveryUpdatedEvent event) {
        developerProjectionService.applyDiscoverySync(event);
    }

    // ------------------------------------------------------------------
    // Contribution
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReputationUpdated(ReputationUpdatedEvent event) {
        developerProjectionService.applyReputationUpdated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionRecorded(ContributionRecordedEvent event) {
        developerProjectionService.applyContributionRecorded(event);
    }

    // ------------------------------------------------------------------
    // Portfolio
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBadgeAwarded(BadgeAwardedEvent event) {
        developerProjectionService.applyBadgeAwarded(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPortfolioVisibilityChanged(PortfolioVisibilityChangedEvent event) {
        developerProjectionService.applyPortfolioVisibilityChanged(event);
    }

    // ------------------------------------------------------------------
    // Team
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamCreated(TeamCreatedEvent event) {
        teamProjectionService.applyCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoinedEvent event) {
        teamProjectionService.applyMemberJoined(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberLeft(MemberLeftEvent event) {
        teamProjectionService.applyMemberLeft(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(MemberRemovedEvent event) {
        teamProjectionService.applyMemberRemoved(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamLocked(TeamLockedEvent event) {
        teamProjectionService.applyLocked(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamUnlocked(TeamUnlockedEvent event) {
        teamProjectionService.applyUnlocked(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamArchived(TeamArchivedEvent event) {
        teamProjectionService.applyArchived(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamDissolved(TeamDissolvedEvent event) {
        teamProjectionService.applyDissolved(event);
    }
}
