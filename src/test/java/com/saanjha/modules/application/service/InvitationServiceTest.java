package com.saanjha.modules.application.service;

import com.saanjha.modules.application.dto.InvitationRequestDTOs.*;
import com.saanjha.modules.application.dto.InvitationResponseDTOs.InvitationResponse;
import com.saanjha.modules.application.entity.Invitation;
import com.saanjha.modules.application.entity.InvitationStatus;
import com.saanjha.modules.application.repository.InvitationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private ProjectService projectService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InvitationService invitationService;

    private UUID projectId;
    private UUID leadUserId;
    private UUID inviteeId;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(invitationRepository, projectService, eventPublisher);
        projectId = UUID.randomUUID();
        leadUserId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();
        lenient().when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void send_happyPath_succeeds() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(any(), any(), any())).thenReturn(false);

        InvitationResponse response = invitationService.sendInvitation(
                projectId, leadUserId, new SendInvitationRequest(inviteeId, "Frontend", "Join us!"));

        assertThat(response.status()).isEqualTo("SENT");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void send_toSelf_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());

        assertThatThrownBy(() -> invitationService.sendInvitation(
                projectId, leadUserId, new SendInvitationRequest(leadUserId, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void send_toArchivedProject_isRejected() {
        ProjectSnapshot archived = new ProjectSnapshot(projectId, leadUserId, "ARCHIVED", "PUBLIC", 5, 1);
        when(projectService.getSnapshot(projectId)).thenReturn(archived);

        assertThatThrownBy(() -> invitationService.sendInvitation(
                projectId, leadUserId, new SendInvitationRequest(inviteeId, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_READ_ONLY));
    }

    @Test
    void send_duplicatePendingInvitation_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(recruitingSnapshot());
        when(invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> invitationService.sendInvitation(
                projectId, leadUserId, new SendInvitationRequest(inviteeId, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void accept_openInvitation_publishesAcceptedEventWithoutTouchingApplications() {
        Invitation invitation = sentInvitation();
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));

        InvitationResponse response = invitationService.accept(invitation.getId());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(eventPublisher).publishEvent(argThat(event -> event.getClass().getSimpleName().equals("InvitationAcceptedEvent")));
    }

    @Test
    void accept_alreadyDeclinedInvitation_isRejected() {
        Invitation invitation = sentInvitation();
        invitation.setStatus(InvitationStatus.DECLINED);
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.accept(invitation.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void revoke_openInvitation_succeeds() {
        Invitation invitation = sentInvitation();
        when(invitationRepository.findWithLockById(invitation.getId())).thenReturn(Optional.of(invitation));

        InvitationResponse response = invitationService.revoke(invitation.getId(), leadUserId, new RevokeInvitationRequest("Role filled."));

        assertThat(response.status()).isEqualTo("REVOKED");
        verify(eventPublisher).publishEvent(argThat(event -> event.getClass().getSimpleName().equals("InvitationRevokedEvent")));
    }

    private ProjectSnapshot recruitingSnapshot() {
        return new ProjectSnapshot(projectId, leadUserId, "RECRUITING", "PUBLIC", 5, 2);
    }

    private Invitation sentInvitation() {
        Invitation invitation = new Invitation();
        invitation.setId(UUID.randomUUID());
        invitation.setProjectId(projectId);
        invitation.setInvitedUserId(inviteeId);
        invitation.setInvitedBy(leadUserId);
        invitation.setStatus(InvitationStatus.SENT);
        invitation.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        return invitation;
    }
}
