package com.saanjha.modules.application.service;

import com.saanjha.modules.application.dto.ApplicationRequestDTOs.*;
import com.saanjha.modules.application.dto.ApplicationResponseDTOs.ApplicationResponse;
import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.entity.ProjectApplication;
import com.saanjha.modules.application.repository.ApplicationNoteRepository;
import com.saanjha.modules.application.repository.ApplicationStatusLogRepository;
import com.saanjha.modules.application.repository.ProjectApplicationRepository;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ProjectApplicationRepository applicationRepository;
    @Mock private ApplicationNoteRepository noteRepository;
    @Mock private ApplicationStatusLogRepository statusLogRepository;
    @Mock private ProjectService projectService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ApplicationService applicationService;

    private UUID projectId;
    private UUID leadUserId;
    private UUID applicantId;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository, noteRepository, statusLogRepository, projectService, eventPublisher);
        projectId = UUID.randomUUID();
        leadUserId = UUID.randomUUID();
        applicantId = UUID.randomUUID();
        lenient().when(applicationRepository.save(any(ProjectApplication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProjectSnapshot recruitingSnapshot() {
        return new ProjectSnapshot(projectId, leadUserId, "RECRUITING", "PUBLIC", 5, 2);
    }

    private SubmitApplicationRequest defaultRequest() {
        return new SubmitApplicationRequest("I'd love to help build this.", "Backend", 10, "UTC");
    }

    // ========================================================================
    // SUBMISSION VALIDATION
    // ========================================================================

    @Test
    void submit_happyPath_succeeds() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(applicationRepository.countByApplicantIdAndStatusIn(any(), any())).thenReturn(0L);

        ApplicationResponse response = applicationService.submitApplication(applicantId, projectId, defaultRequest());

        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.applicantId()).isEqualTo(applicantId);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void submit_toOwnProject_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());

        assertThatThrownBy(() -> applicationService.submitApplication(leadUserId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void submit_toNonRecruitingProject_isRejected() {
        ProjectSnapshot draft = new ProjectSnapshot(projectId, leadUserId, "DRAFT", "PUBLIC", 5, 1);
        when(projectService.getSnapshot(projectId)).thenReturn(draft);

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_ACCEPTING_APPLICATIONS));
    }

    @Test
    void submit_toInviteOnlyProject_isForbidden() {
        ProjectSnapshot inviteOnly = new ProjectSnapshot(projectId, leadUserId, "RECRUITING", "INVITE_ONLY", 5, 1);
        when(projectService.getSnapshot(projectId)).thenReturn(inviteOnly);

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void submit_whenTeamIsFull_isRejected() {
        ProjectSnapshot full = new ProjectSnapshot(projectId, leadUserId, "RECRUITING", "PUBLIC", 3, 3);
        when(projectService.getSnapshot(projectId)).thenReturn(full);

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_ACCEPTING_APPLICATIONS));
    }

    @Test
    void submit_withExistingActiveApplication_isDuplicateRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_APP));
    }

    @Test
    void submit_withinCooldownOfPreviousRejection_isDuplicateRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(any(), any(), any())).thenReturn(false);

        ProjectApplication previous = new ProjectApplication();
        previous.setStatus(ApplicationStatus.REJECTED);
        previous.setUpdatedAt(Instant.now().minus(2, ChronoUnit.HOURS)); // well within the 24h cooldown
        when(applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_APP));
    }

    @Test
    void submit_afterCooldownElapsed_isAllowed() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(any(), any(), any())).thenReturn(false);

        ProjectApplication previous = new ProjectApplication();
        previous.setStatus(ApplicationStatus.REJECTED);
        previous.setUpdatedAt(Instant.now().minus(48, ChronoUnit.HOURS)); // past the 24h cooldown
        when(applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(previous));
        when(applicationRepository.countByApplicantIdAndStatusIn(any(), any())).thenReturn(0L);

        ApplicationResponse response = applicationService.submitApplication(applicantId, projectId, defaultRequest());

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void submit_atActiveApplicationCap_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(applicationRepository.existsByProjectIdAndApplicantIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(applicationRepository.findFirstByProjectIdAndApplicantIdOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(applicationRepository.countByApplicantIdAndStatusIn(any(), any())).thenReturn(5L); // at the cap

        assertThatThrownBy(() -> applicationService.submitApplication(applicantId, projectId, defaultRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ========================================================================
    // WITHDRAWAL
    // ========================================================================

    @Test
    void withdraw_openApplication_succeeds() {
        ProjectApplication application = openApplication();
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        ApplicationResponse response = applicationService.withdraw(application.getId(), applicantId);

        assertThat(response.status()).isEqualTo("WITHDRAWN");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void withdraw_alreadyAcceptedApplication_isRejected() {
        ProjectApplication application = openApplication();
        application.setStatus(ApplicationStatus.ACCEPTED);
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.withdraw(application.getId(), applicantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    // ========================================================================
    // REVIEW WORKFLOW
    // ========================================================================

    @Test
    void accept_publishesApplicationAcceptedEvent() {
        ProjectApplication application = openApplication();
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        ApplicationResponse response = applicationService.accept(application.getId(), leadUserId, new ReviewDecisionRequest(null));

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.reviewedBy()).isEqualTo(leadUserId);
        verify(eventPublisher).publishEvent(argThat(event ->
                event.getClass().getSimpleName().equals("ApplicationAcceptedEvent")));
    }

    @Test
    void reject_storesDecisionReasonAndPublishesEvent() {
        ProjectApplication application = openApplication();
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        ApplicationResponse response = applicationService.reject(application.getId(), leadUserId, new ReviewDecisionRequest("Not a fit right now."));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.decisionReason()).isEqualTo("Not a fit right now.");
        verify(eventPublisher).publishEvent(argThat(event ->
                event.getClass().getSimpleName().equals("ApplicationRejectedEvent")));
    }

    @Test
    void reopen_fromRejected_returnsToUnderReview() {
        ProjectApplication application = openApplication();
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecisionReason("Previously rejected.");
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        ApplicationResponse response = applicationService.reopen(application.getId(), leadUserId, new ReviewDecisionRequest("Reconsidering."));

        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        verify(eventPublisher).publishEvent(argThat(event ->
                event.getClass().getSimpleName().equals("ApplicationReopenedEvent")));
    }

    @Test
    void reopen_fromAccepted_isRejected() {
        ProjectApplication application = openApplication();
        application.setStatus(ApplicationStatus.ACCEPTED);
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.reopen(application.getId(), leadUserId, new ReviewDecisionRequest(null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private ProjectApplication openApplication() {
        ProjectApplication application = new ProjectApplication();
        application.setId(UUID.randomUUID());
        application.setProjectId(projectId);
        application.setApplicantId(applicantId);
        application.setMessage("Test message");
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setExpiresAt(Instant.now().plus(21, ChronoUnit.DAYS));
        return application;
    }
}
