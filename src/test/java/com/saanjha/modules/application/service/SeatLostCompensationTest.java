package com.saanjha.modules.application.service;

import com.saanjha.modules.application.entity.*;
import com.saanjha.modules.application.repository.ApplicationNoteRepository;
import com.saanjha.modules.application.repository.ApplicationStatusLogRepository;
import com.saanjha.modules.application.repository.InvitationRepository;
import com.saanjha.modules.application.repository.ProjectApplicationRepository;
import com.saanjha.modules.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TD19 (architecture-review.md §9.2): proves the compensating transitions
 * that close the "ghost acceptance" gap actually fire, and are idempotent
 * against duplicate event redelivery.
 */
@ExtendWith(MockitoExtension.class)
class SeatLostCompensationTest {

    @Mock private ProjectApplicationRepository applicationRepository;
    @Mock private ApplicationNoteRepository noteRepository;
    @Mock private ApplicationStatusLogRepository statusLogRepository;
    @Mock private ProjectService projectService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @Mock private InvitationRepository invitationRepository;
    @Mock private ApplicationEventPublisher invitationEventPublisher;

    private ApplicationService applicationService;
    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository, noteRepository, statusLogRepository, projectService, applicationEventPublisher);
        invitationService = new InvitationService(invitationRepository, projectService, invitationEventPublisher);
        lenient().when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reopenAfterSeatLost_fromAccepted_movesToUnderReviewWithSystemNote() {
        ProjectApplication application = acceptedApplication();
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        applicationService.reopenAfterSeatLost(application.getId(), "team reached capacity");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(application.getDecisionReason()).isNull();
        verify(noteRepository).save(any(ApplicationNote.class));
        verify(applicationEventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("ApplicationReopenedEvent")));
    }

    @Test
    void reopenAfterSeatLost_whenAlreadyWithdrawn_isIdempotentNoOp() {
        ProjectApplication application = acceptedApplication();
        application.setStatus(ApplicationStatus.WITHDRAWN);
        when(applicationRepository.findWithLockById(application.getId())).thenReturn(Optional.of(application));

        applicationService.reopenAfterSeatLost(application.getId(), "team reached capacity");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN); // untouched
        verifyNoInteractions(noteRepository);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void markSeatLost_fromAccepted_transitionsToSeatLostAndPublishes() {
        Invitation invitation = acceptedInvitation();
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));

        invitationService.markSeatLost(invitation.getId(), "team reached capacity");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.SEAT_LOST);
        verify(invitationEventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("InvitationSeatLostEvent")));
    }

    @Test
    void markSeatLost_whenAlreadyDeclined_isIdempotentNoOp() {
        Invitation invitation = acceptedInvitation();
        invitation.setStatus(InvitationStatus.DECLINED);
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));

        invitationService.markSeatLost(invitation.getId(), "team reached capacity");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED); // untouched
        verifyNoInteractions(invitationEventPublisher);
    }

    private ProjectApplication acceptedApplication() {
        ProjectApplication application = new ProjectApplication();
        application.setId(UUID.randomUUID());
        application.setProjectId(UUID.randomUUID());
        application.setApplicantId(UUID.randomUUID());
        application.setMessage("test");
        application.setStatus(ApplicationStatus.ACCEPTED);
        application.setDecisionReason(null);
        application.setExpiresAt(Instant.now().plus(21, ChronoUnit.DAYS));
        return application;
    }

    private Invitation acceptedInvitation() {
        Invitation invitation = new Invitation();
        invitation.setId(UUID.randomUUID());
        invitation.setProjectId(UUID.randomUUID());
        invitation.setInvitedUserId(UUID.randomUUID());
        invitation.setInvitedBy(UUID.randomUUID());
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        return invitation;
    }
}
