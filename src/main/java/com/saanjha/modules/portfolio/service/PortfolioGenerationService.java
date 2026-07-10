package com.saanjha.modules.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.portfolio.entity.PortfolioEntry;
import com.saanjha.modules.portfolio.entity.PortfolioGenerationState;
import com.saanjha.modules.portfolio.entity.PortfolioSummary;
import com.saanjha.modules.portfolio.entity.ProjectCompletionSignal;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioEntryCreatedEvent;
import com.saanjha.modules.portfolio.event.PortfolioEvents.PortfolioGeneratedEvent;
import com.saanjha.modules.portfolio.repository.PortfolioEntryRepository;
import com.saanjha.modules.portfolio.repository.PortfolioGenerationStateRepository;
import com.saanjha.modules.portfolio.repository.PortfolioSummaryRepository;
import com.saanjha.modules.portfolio.repository.ProjectCompletionSignalRepository;
import com.saanjha.modules.project.service.ProjectSnapshotProvider;
import com.saanjha.modules.project.service.ProjectSnapshotProvider.ProjectSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the one genuinely subtle piece of this module: deciding WHEN enough
 * information exists to create an immutable {@code PortfolioEntry}, given
 * that its three inputs (running contribution totals, team roster/tenure,
 * project completion gate) arrive independently and in no guaranteed order.
 * See {@code PortfolioGenerationState}'s Javadoc for the full reasoning.
 *
 * Every public method here is safe to call redundantly — redelivery of any
 * upstream event must never create a duplicate entry or double-count a
 * rollup. This is enforced structurally: {@code generated} is checked
 * before every entry creation, under a pessimistic row lock on the
 * generation-state row for that (project, user) pair.
 */
@Service
@RequiredArgsConstructor
public class PortfolioGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioGenerationService.class);

    private final PortfolioGenerationStateRepository stateRepository;
    private final ProjectCompletionSignalRepository completionSignalRepository;
    private final PortfolioEntryRepository entryRepository;
    private final PortfolioSummaryRepository summaryRepository;
    private final PortfolioTimelineService timelineService;
    private final PortfolioBadgeEngine badgeEngine;
    private final ProjectSnapshotProvider projectSnapshotProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // CONTRIBUTION ACCUMULATION (runs continuously, well before completion)
    // ========================================================================

    @Transactional
    public void accumulateContribution(UUID projectId, UUID userId, double finalScore, String contributionType) {
        if (projectId == null || userId == null) {
            return; // Platform-wide / non-project-scoped contributions don't feed a project showcase.
        }
        PortfolioGenerationState state = stateRepository.findWithLockByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> PortfolioGenerationState.blank(projectId, userId));
        if (state.isGenerated()) {
            // Entry already finalized for this pair; a late-arriving contribution (e.g. a
            // correction's replacement entry) does not retroactively reopen frozen history.
            return;
        }
        boolean isTaskCompletion = "TASK_COMPLETION".equals(contributionType);
        boolean isReview = "TASK_REVIEW".equals(contributionType);
        state.addContribution(finalScore, isTaskCompletion, isReview);
        stateRepository.save(state);
    }

    // ========================================================================
    // TEAM-SIDE SIGNAL
    // ========================================================================

    @Transactional
    public void applyTeamRoster(UUID projectId, List<RosterMember> roster) {
        Optional<ProjectCompletionSignal> completionSignal = completionSignalRepository.findByProjectId(projectId);

        for (RosterMember member : roster) {
            PortfolioGenerationState state = stateRepository.findWithLockByProjectIdAndUserId(projectId, member.userId())
                    .orElseGet(() -> PortfolioGenerationState.blank(projectId, member.userId()));
            if (state.isGenerated()) {
                continue;
            }
            state.applyTeamData(member.role(), member.contributionTitle(), member.joinedAt(), member.leftAt(), member.tenureDays());
            stateRepository.save(state);

            completionSignal.ifPresent(signal -> tryGenerate(projectId, member.userId(), signal));
        }
    }

    // ========================================================================
    // PROJECT-SIDE GATE
    // ========================================================================

    @Transactional
    public void applyProjectCompletion(UUID projectId, UUID leadUserId, Instant completedAt) {
        if (completionSignalRepository.findByProjectId(projectId).isEmpty()) {
            completionSignalRepository.save(ProjectCompletionSignal.create(projectId, completedAt, leadUserId));
        }
        ProjectCompletionSignal signal = completionSignalRepository.findByProjectId(projectId).orElseThrow();

        List<PortfolioGenerationState> readyStates = stateRepository.findByProjectIdAndGeneratedFalse(projectId);
        for (PortfolioGenerationState state : readyStates) {
            if (state.isTeamDataArrived()) {
                tryGenerate(projectId, state.getUserId(), signal);
            }
        }
    }

    // ========================================================================
    // REPUTATION / CORRECTIONS (global rollup only — see class Javadoc on corrections)
    // ========================================================================

    @Transactional
    public void applyReputationUpdate(UUID userId, Double reliabilityScore, Double leadershipScore,
                                       Double consistencyScore, Double reviewQualityScore) {
        PortfolioSummary summary = summaryRepository.findById(userId).orElseGet(() -> PortfolioSummary.blank(userId));
        summary.setReliabilityScore(reliabilityScore);
        summary.setLeadershipScore(leadershipScore);
        summary.setConsistencyScore(consistencyScore);
        summary.setReviewQualityScore(reviewQualityScore);
        summary.setUpdatedAt(Instant.now());
        summaryRepository.save(summary);
    }

    /**
     * Per {@code ContributionCorrectedEvent}'s own Javadoc ("Portfolio must
     * reverse the same amount it previously added"). No projectId travels
     * with a correction, so this can only adjust the GLOBAL rollup — an
     * already-generated, project-scoped {@code PortfolioEntry} stays frozen
     * exactly as it was. See the module write-up's Known Tradeoffs.
     */
    @Transactional
    public void applyContributionCorrection(UUID userId, double scoreDelta) {
        PortfolioSummary summary = summaryRepository.findById(userId).orElse(null);
        if (summary == null) {
            return; // Nothing to reverse — this user has no Portfolio rollup yet.
        }
        summary.setTotalVerifiedScore(summary.getTotalVerifiedScore() + scoreDelta);
        summary.setUpdatedAt(Instant.now());
        summaryRepository.save(summary);
    }

    /** Project was abandoned, not completed — per the module's core principle, abandoned work never becomes verified history. */
    @Transactional
    public void discardPending(UUID projectId) {
        List<PortfolioGenerationState> pending = stateRepository.findByProjectId(projectId);
        pending.stream().filter(s -> !s.isGenerated()).forEach(stateRepository::delete);
        // Any completion signal here would be a genuine inconsistency (both terminal events
        // for the same project firing) — defensively left alone rather than deleted, since a
        // signal existing means a completion WAS observed and downstream entries may already exist.
    }

    // ========================================================================
    // GENERATION
    // ========================================================================

    private void tryGenerate(UUID projectId, UUID userId, ProjectCompletionSignal signal) {
        PortfolioGenerationState state = stateRepository.findWithLockByProjectIdAndUserId(projectId, userId).orElse(null);
        if (state == null || state.isGenerated() || !state.isTeamDataArrived()) {
            return;
        }
        if (entryRepository.existsByUserIdAndProjectId(userId, projectId)) {
            // Defensive: entry already exists (e.g. a prior attempt succeeded but the state
            // flag update lost a race). Mark state consistent and stop — never a duplicate row,
            // the unique (user_id, project_id) constraint would reject it anyway.
            state.setGenerated(true);
            stateRepository.save(state);
            return;
        }

        Optional<ProjectSnapshot> snapshot = projectSnapshotProvider.getSnapshot(projectId);
        if (snapshot.isEmpty()) {
            log.error("PortfolioGenerationService: no ProjectSnapshot available for project {} (user {}); leaving state pending for retry.", projectId, userId);
            return;
        }

        boolean wasLead = "LEAD".equalsIgnoreCase(state.getRole());
        boolean firstLeadEntry = wasLead && !entryRepository.existsByUserIdAndWasLeadTrue(userId);

        PortfolioEntry entry = PortfolioEntry.create(
                userId, projectId,
                snapshot.get().title(), snapshot.get().slug(), snapshot.get().category(),
                snapshot.get().descriptionExcerpt(), writeTechnologies(snapshot.get().technologyTags()),
                normalizeRole(state.getRole()), wasLead, state.getContributionTitle(),
                state.getJoinedAt(), state.getLeftAt(), state.getTenureDays(),
                state.getRunningScore(), state.getRunningTasksCompleted(), state.getRunningReviewsGiven(),
                signal.getCompletedAt());
        entry = entryRepository.save(entry);

        state.setGenerated(true);
        stateRepository.save(state);

        PortfolioSummary summary = summaryRepository.findById(userId).orElseGet(() -> PortfolioSummary.blank(userId));
        summary.applyEntry(entry);
        summaryRepository.save(summary);

        timelineService.recordProjectCompleted(userId, projectId, snapshot.get().title(), wasLead, entry.getGeneratedAt());

        long backendCount = countTaggedCompletions(userId, true);
        long frontendCount = countTaggedCompletions(userId, false);
        badgeEngine.evaluateOnEntryGenerated(userId, wasLead, firstLeadEntry, snapshot.get(), backendCount, frontendCount);

        eventPublisher.publishEvent(new PortfolioEntryCreatedEvent(entry.getId(), userId, projectId, entry.getContributionScore(), wasLead, entry.getGeneratedAt()));
        eventPublisher.publishEvent(new PortfolioGeneratedEvent(userId, projectId, entry.getGeneratedAt()));
    }

    private long countTaggedCompletions(UUID userId, boolean backend) {
        return entryRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .filter(e -> {
                    List<String> tags = readTechnologies(e.getTechnologiesJson());
                    ProjectSnapshot pseudoSnapshot = new ProjectSnapshot(e.getProjectId(), e.getProjectTitle(), e.getProjectSlug(), e.getProjectCategory(), e.getProjectDescriptionExcerpt(), tags);
                    return backend ? badgeEngine.projectHasBackendTags(pseudoSnapshot) : badgeEngine.projectHasFrontendTags(pseudoSnapshot);
                })
                .count();
    }

    private String normalizeRole(String role) {
        return role == null ? "MEMBER" : role.toUpperCase();
    }

    private String writeTechnologies(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception ex) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readTechnologies(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Flat carrier decoupling this service from Team's own {@code ArchivedMember} record, per the module boundary rule (no other module's event-payload types leak past the listener). */
    public record RosterMember(UUID userId, String role, String contributionTitle, Instant joinedAt, Instant leftAt, Long tenureDays) {}
}
