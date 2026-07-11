package com.saanjha.modules.application.service;

import com.saanjha.modules.application.dto.ApplicationRequestDTOs.*;
import com.saanjha.modules.application.dto.ApplicationResponseDTOs.*;
import com.saanjha.modules.application.entity.ApplicationNote;
import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.entity.ApplicationStatusLog;
import com.saanjha.modules.application.entity.ProjectApplication;
import com.saanjha.modules.application.event.ApplicationEvents.*;
import com.saanjha.modules.application.repository.ApplicationNoteRepository;
import com.saanjha.modules.application.repository.ApplicationStatusLogRepository;
import com.saanjha.modules.application.repository.ProjectApplicationRepository;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.security.HtmlSanitizer;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the full recruitment workflow for direct applications
 * (the {@link com.saanjha.modules.application.entity.Invitation} entry
 * point is handled by the sibling {@code InvitationService}).
 *
 * Every project-state check goes through {@code ProjectService.getSnapshot()}
 * — the sanctioned cross-module service-interface call — never through a
 * direct query against Project's schema.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** Spec's own Global Error Code Registry ties DUPLICATE_APP to a 24h window. */
    private static final long REAPPLICATION_COOLDOWN_HOURS = 24;

    /** How long an open application stays open before the sweep auto-expires it. */
    private static final long APPLICATION_EXPIRY_DAYS = 21;

    /** Platform-wide cap on simultaneously open applications, across all projects, for one user. */
    private static final int MAX_ACTIVE_APPLICATIONS_PER_USER = 5;

    private static final List<ApplicationStatus> ACTIVE_STATUSES =
            List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED);

    private final ProjectApplicationRepository applicationRepository;
    private final ApplicationNoteRepository noteRepository;
    private final ApplicationStatusLogRepository statusLogRepository;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;

    // ========================================================================
    // SUBMISSION
    // ========================================================================

    @Transactional
    public ApplicationResponse submitApplication(UUID applicantId, UUID projectId, SubmitApplicationRequest request) {
        ProjectSnapshot project = projectService.getSnapshot(projectId);

        assertNotOwnProject(project, applicantId);
        assertProjectAcceptingApplications(project);
        assertTeamHasOpenSlots(project);
        assertNoActiveApplication(projectId, applicantId);
        assertReapplicationCooldownElapsed(projectId, applicantId);
        assertUnderActiveApplicationCap(applicantId);

        ProjectApplication application = new ProjectApplication();
        application.setProjectId(projectId);
        application.setApplicantId(applicantId);
        application.setMessage(HtmlSanitizer.sanitize(request.message()));
        application.setPreferredRole(request.preferredRole());
        application.setWeeklyHours(request.weeklyHours());
        application.setTimezone(request.timezone());
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setExpiresAt(Instant.now().plus(APPLICATION_EXPIRY_DAYS, ChronoUnit.DAYS));

        application = applicationRepository.save(application);
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(application.getId(), projectId, applicantId, project.leadUserId(), Instant.now()));

        return mapToResponse(application);
    }

    // ========================================================================
    // READS
    // ========================================================================

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(UUID applicationId) {
        return mapToResponse(getApplicationOrThrow(applicationId));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationSummaryResponse> listMyApplications(UUID applicantId, Pageable pageable) {
        return applicationRepository.findByApplicantId(applicantId, pageable).map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationSummaryResponse> listApplicationsForProject(UUID projectId, ApplicationStatus statusFilter, Pageable pageable) {
        Page<ProjectApplication> page = statusFilter != null
                ? applicationRepository.findByProjectIdAndStatus(projectId, statusFilter, pageable)
                : applicationRepository.findByProjectId(projectId, pageable);
        return page.map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusLogResponse> getTimeline(UUID applicationId) {
        return statusLogRepository.findByApplicationIdOrderByChangedAtAsc(applicationId).stream()
                .map(log -> new ApplicationStatusLogResponse(log.getFromStatus(), log.getToStatus(), log.getChangedBy(), log.getReason(), log.getChangedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationNoteResponse> getNotes(UUID applicationId) {
        return noteRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(n -> new ApplicationNoteResponse(n.getId(), n.getAuthorId(), n.getNote(), n.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationStatsResponse getStats(UUID projectId) {
        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (ProjectApplicationRepository.StatusCount row : applicationRepository.countByStatusForProject(projectId)) {
            counts.put(row.getStatus().name(), row.getCount());
            total += row.getCount();
        }
        return new ApplicationStatsResponse(projectId, counts, total);
    }

    // ========================================================================
    // WITHDRAWAL (applicant-initiated)
    // ========================================================================

    @Transactional
    public ApplicationResponse withdraw(UUID applicationId, UUID applicantId) {
        ProjectApplication application = applicationRepository.findWithLockById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));

        ApplicationStatus from = application.getStatus();
        ApplicationStatusTransitionValidator.assertLegal(from, ApplicationStatus.WITHDRAWN);

        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setWithdrawnAt(Instant.now());
        application = applicationRepository.save(application);

        statusLogRepository.save(new ApplicationStatusLog(applicationId, from, ApplicationStatus.WITHDRAWN, applicantId, null));
        UUID withdrawnProjectLeadId = projectService.getSnapshot(application.getProjectId()).leadUserId();
        eventPublisher.publishEvent(new ApplicationWithdrawnEvent(applicationId, application.getProjectId(), applicantId, withdrawnProjectLeadId, Instant.now()));

        return mapToResponse(application);
    }

    // ========================================================================
    // REVIEW (Lead/Admin-initiated)
    // ========================================================================

    @Transactional
    public ApplicationResponse markUnderReview(UUID applicationId, UUID reviewerId) {
        return transitionForReview(applicationId, reviewerId, ApplicationStatus.UNDER_REVIEW, null);
    }

    @Transactional
    public ApplicationResponse shortlist(UUID applicationId, UUID reviewerId) {
        ApplicationResponse response = transitionForReview(applicationId, reviewerId, ApplicationStatus.SHORTLISTED, null);
        eventPublisher.publishEvent(new ApplicationShortlistedEvent(applicationId, response.projectId(), response.applicantId(), Instant.now()));
        return response;
    }

    @Transactional
    public ApplicationResponse accept(UUID applicationId, UUID reviewerId, ReviewDecisionRequest request) {
        ApplicationResponse response = transitionForReview(applicationId, reviewerId, ApplicationStatus.ACCEPTED, request.reason());
        eventPublisher.publishEvent(new ApplicationAcceptedEvent(applicationId, response.projectId(), response.applicantId(), reviewerId, Instant.now()));
        return response;
    }

    @Transactional
    public ApplicationResponse reject(UUID applicationId, UUID reviewerId, ReviewDecisionRequest request) {
        ApplicationResponse response = transitionForReview(applicationId, reviewerId, ApplicationStatus.REJECTED, request.reason());
        eventPublisher.publishEvent(new ApplicationRejectedEvent(applicationId, response.projectId(), response.applicantId(), reviewerId, request.reason(), Instant.now()));
        return response;
    }

    @Transactional
    public ApplicationResponse reopen(UUID applicationId, UUID actingUserId, ReviewDecisionRequest request) {
        ProjectApplication application = applicationRepository.findWithLockById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));

        ApplicationStatus from = application.getStatus();
        ApplicationStatusTransitionValidator.assertLegal(from, ApplicationStatus.UNDER_REVIEW);

        application.setStatus(ApplicationStatus.UNDER_REVIEW);
        application.setDecisionReason(null);
        application = applicationRepository.save(application);

        statusLogRepository.save(new ApplicationStatusLog(applicationId, from, ApplicationStatus.UNDER_REVIEW, actingUserId, request.reason()));
        eventPublisher.publishEvent(new ApplicationReopenedEvent(applicationId, application.getProjectId(), application.getApplicantId(), actingUserId, Instant.now()));

        return mapToResponse(application);
    }

    /**
     * FIX (TD19, architecture-review.md §9.2): compensating transition for
     * the last-slot overbooking race. Called exclusively by the listener
     * reacting to Team's {@code MembershipCreationRejectedEvent} — never
     * exposed via any controller endpoint (mirrors {@code systemArchive}/
     * {@code systemExpire}'s internal-only pattern in Project/Application).
     *
     * Idempotent by construction: if the application has already moved off
     * ACCEPTED by the time this runs (e.g. a duplicate event redelivery, or
     * the applicant already withdrew), this is a silent no-op rather than an
     * error — duplicate events must never corrupt state.
     */
    @Transactional
    public void reopenAfterSeatLost(UUID applicationId, String reason) {
        ProjectApplication application = applicationRepository.findWithLockById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));

        ApplicationStatus from = application.getStatus();
        if (!ApplicationStatusTransitionValidator.isLegal(from, ApplicationStatus.UNDER_REVIEW)) {
            return; // Already moved on (withdrawn, re-reviewed, etc.) — nothing to compensate.
        }

        application.setStatus(ApplicationStatus.UNDER_REVIEW);
        application.setDecisionReason(null);
        application = applicationRepository.save(application);

        String note = "Automatically reopened: this applicant's acceptance could not be seated (" + reason + "). "
                + "Please review and decide again.";
        statusLogRepository.save(new ApplicationStatusLog(applicationId, from, ApplicationStatus.UNDER_REVIEW, ApplicationStatusLog.SYSTEM_ACTOR_ID, note));
        noteRepository.save(new ApplicationNote(applicationId, ApplicationStatusLog.SYSTEM_ACTOR_ID, note));
        eventPublisher.publishEvent(new ApplicationReopenedEvent(applicationId, application.getProjectId(), application.getApplicantId(), ApplicationStatusLog.SYSTEM_ACTOR_ID, Instant.now()));
    }

    @Transactional
    public ApplicationNoteResponse addNote(UUID applicationId, UUID authorId, AddNoteRequest request) {
        getApplicationOrThrow(applicationId); // 404s cleanly if the application doesn't exist
        ApplicationNote note = new ApplicationNote(applicationId, authorId, HtmlSanitizer.sanitize(request.note()));
        note = noteRepository.save(note);
        return new ApplicationNoteResponse(note.getId(), note.getAuthorId(), note.getNote(), note.getCreatedAt());
    }

    @Transactional
    public BulkReviewResultResponse bulkReview(UUID projectId, UUID reviewerId, BulkReviewRequest request) {
        List<UUID> succeeded = new java.util.ArrayList<>();
        Map<String, String> failed = new HashMap<>();

        for (String rawId : request.applicationIds()) {
            UUID applicationId;
            try {
                applicationId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                failed.put(rawId, "Not a valid application id.");
                continue;
            }
            try {
                ProjectApplication application = getApplicationOrThrow(applicationId);
                if (!application.getProjectId().equals(projectId)) {
                    failed.put(rawId, "Application does not belong to this project.");
                    continue;
                }
                ReviewDecisionRequest decision = new ReviewDecisionRequest(request.reason());
                switch (request.action()) {
                    case "SHORTLIST" -> shortlist(applicationId, reviewerId);
                    case "ACCEPT" -> accept(applicationId, reviewerId, decision);
                    case "REJECT" -> reject(applicationId, reviewerId, decision);
                    default -> throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown bulk action.");
                }
                succeeded.add(applicationId);
            } catch (AppException ex) {
                // One bad row must never abort the rest of the batch — report per-item outcomes instead.
                failed.put(rawId, ex.getMessage());
            }
        }

        return new BulkReviewResultResponse(succeeded, failed);
    }

    /**
     * Internal entry point for the expiration sweep. Bypasses the
     * @PreAuthorize-guarded controller path entirely, same rationale as
     * {@code ProjectService.systemArchive}.
     */
    @Transactional
    public void systemExpire(UUID applicationId) {
        ProjectApplication application = applicationRepository.findWithLockById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));

        ApplicationStatus from = application.getStatus();
        if (!ApplicationStatusTransitionValidator.isLegal(from, ApplicationStatus.EXPIRED)) {
            return; // Already moved on by the time the sweep runs.
        }

        application.setStatus(ApplicationStatus.EXPIRED);
        applicationRepository.save(application);

        statusLogRepository.save(new ApplicationStatusLog(
                applicationId, from, ApplicationStatus.EXPIRED, ApplicationStatusLog.SYSTEM_ACTOR_ID, "Auto-expired after " + APPLICATION_EXPIRY_DAYS + " days."));
        eventPublisher.publishEvent(new ApplicationExpiredEvent(applicationId, application.getProjectId(), application.getApplicantId(), Instant.now()));
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private ApplicationResponse transitionForReview(UUID applicationId, UUID reviewerId, ApplicationStatus target, String reason) {
        ProjectApplication application = applicationRepository.findWithLockById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));

        ApplicationStatus from = application.getStatus();
        ApplicationStatusTransitionValidator.assertLegal(from, target);

        application.setStatus(target);
        if (target == ApplicationStatus.ACCEPTED || target == ApplicationStatus.REJECTED || target == ApplicationStatus.UNDER_REVIEW) {
            application.setReviewedAt(Instant.now());
            application.setReviewedBy(reviewerId);
        }
        if (target == ApplicationStatus.REJECTED) {
            application.setDecisionReason(reason);
        }

        application = applicationRepository.save(application);
        statusLogRepository.save(new ApplicationStatusLog(applicationId, from, target, reviewerId, reason));

        return mapToResponse(application);
    }

    private ProjectApplication getApplicationOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Application not found."));
    }

    private void assertNotOwnProject(ProjectSnapshot project, UUID applicantId) {
        if (project.leadUserId().equals(applicantId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "You cannot apply to your own project.");
        }
    }

    private void assertProjectAcceptingApplications(ProjectSnapshot project) {
        if (!"RECRUITING".equals(project.status())) {
            throw new AppException(ErrorCode.PROJECT_NOT_ACCEPTING_APPLICATIONS,
                    "This project is not currently recruiting.");
        }
        if ("INVITE_ONLY".equals(project.visibility())) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "This project is invite-only. Ask the project Lead to send you an invitation instead.");
        }
    }

    private void assertTeamHasOpenSlots(ProjectSnapshot project) {
        if (project.currentTeamSize() >= project.maxTeamSize()) {
            throw new AppException(ErrorCode.PROJECT_NOT_ACCEPTING_APPLICATIONS,
                    "This project's team is already at capacity.");
        }
    }

    private void assertNoActiveApplication(UUID projectId, UUID applicantId) {
        if (applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(projectId, applicantId, ACTIVE_STATUSES)) {
            throw new AppException(ErrorCode.DUPLICATE_APP, "You already have an active application to this project.");
        }
    }

    private void assertReapplicationCooldownElapsed(UUID projectId, UUID applicantId) {
        applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(projectId, applicantId)
                .ifPresent(previous -> {
                    Instant cooldownEnd = previous.getUpdatedAt().plus(REAPPLICATION_COOLDOWN_HOURS, ChronoUnit.HOURS);
                    if (previous.isTerminal() && Instant.now().isBefore(cooldownEnd)) {
                        throw new AppException(ErrorCode.DUPLICATE_APP,
                                "You must wait " + REAPPLICATION_COOLDOWN_HOURS + " hours after your last application before reapplying to this project.");
                    }
                });
    }

    private void assertUnderActiveApplicationCap(UUID applicantId) {
        long activeCount = applicationRepository.countByApplicantIdAndStatusIn(applicantId, ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_APPLICATIONS_PER_USER) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "You can have at most " + MAX_ACTIVE_APPLICATIONS_PER_USER + " active applications at once. Withdraw one before applying again.");
        }
    }

    private ApplicationResponse mapToResponse(ProjectApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getProjectId(),
                application.getApplicantId(),
                application.getStatus().name(),
                application.getMessage(),
                application.getPreferredRole(),
                application.getWeeklyHours(),
                application.getTimezone(),
                application.getReviewedAt(),
                application.getReviewedBy(),
                application.getDecisionReason(),
                application.getWithdrawnAt(),
                application.getExpiresAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private ApplicationSummaryResponse mapToSummary(ProjectApplication application) {
        return new ApplicationSummaryResponse(
                application.getId(), application.getProjectId(), application.getApplicantId(),
                application.getStatus().name(), application.getCreatedAt());
    }
}
