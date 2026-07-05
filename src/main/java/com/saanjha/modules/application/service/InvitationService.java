package com.saanjha.modules.application.service;

import com.saanjha.modules.application.dto.InvitationRequestDTOs.*;
import com.saanjha.modules.application.dto.InvitationResponseDTOs.InvitationResponse;
import com.saanjha.modules.application.entity.Invitation;
import com.saanjha.modules.application.entity.InvitationStatus;
import com.saanjha.modules.application.event.InvitationEvents.*;
import com.saanjha.modules.application.repository.InvitationRepository;
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
import java.util.UUID;

/**
 * Manages the Invitation lifecycle — the "Lead reaches out first" entry
 * point into recruitment, as opposed to ApplicationService's "candidate
 * reaches out first". The two are intentionally kept as separate services
 * (not one god-service) since their business rules barely overlap: an
 * invitation has no reapplication cooldown, no application cap, and no
 * "team full" check performed here (the Lead is explicitly choosing to
 * exceed-or-fill their own roster; that judgment call is theirs to make,
 * enforced instead at the point Team actually creates the membership).
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final long INVITATION_EXPIRY_DAYS = 14;

    private final InvitationRepository invitationRepository;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;

    // ========================================================================
    // SENDING
    // ========================================================================

    @Transactional
    public InvitationResponse sendInvitation(UUID projectId, UUID leadUserId, SendInvitationRequest request) {
        ProjectSnapshot project = projectService.getSnapshot(projectId);

        if (!"DRAFT".equals(project.status()) && !"RECRUITING".equals(project.status())) {
            throw new AppException(ErrorCode.PROJECT_READ_ONLY,
                    "Cannot invite members to a project that is " + project.status() + ".");
        }
        if (request.invitedUserId().equals(leadUserId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "You cannot invite yourself to your own project.");
        }
        if (invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(projectId, request.invitedUserId(), InvitationStatus.SENT)) {
            throw new AppException(ErrorCode.CONFLICT, "This user already has a pending invitation to this project.");
        }

        Invitation invitation = new Invitation();
        invitation.setProjectId(projectId);
        invitation.setInvitedUserId(request.invitedUserId());
        invitation.setInvitedBy(leadUserId);
        invitation.setPreferredRole(request.preferredRole());
        invitation.setMessage(HtmlSanitizer.sanitize(request.message()));
        invitation.setStatus(InvitationStatus.SENT);
        invitation.setExpiresAt(Instant.now().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS));

        invitation = invitationRepository.save(invitation);
        eventPublisher.publishEvent(new InvitationSentEvent(invitation.getId(), projectId, request.invitedUserId(), leadUserId, Instant.now()));

        return mapToResponse(invitation);
    }

    // ========================================================================
    // READS
    // ========================================================================

    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(UUID invitationId) {
        return mapToResponse(getInvitationOrThrow(invitationId));
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> listMyInvitations(UUID invitedUserId, Pageable pageable) {
        return invitationRepository.findByInvitedUserId(invitedUserId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> listInvitationsForProject(UUID projectId, Pageable pageable) {
        return invitationRepository.findByProjectId(projectId, pageable).map(this::mapToResponse);
    }

    // ========================================================================
    // RESPONSES (invitee-initiated)
    // ========================================================================

    @Transactional
    public InvitationResponse accept(UUID invitationId) {
        Invitation invitation = lockOpenInvitationOrThrow(invitationId);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        invitation = invitationRepository.save(invitation);

        // Deliberately does NOT create a ProjectApplication row or a Team member —
        // Team is the sole owner of membership creation, reacting to this event.
        eventPublisher.publishEvent(new InvitationAcceptedEvent(invitationId, invitation.getProjectId(), invitation.getInvitedUserId(), Instant.now()));

        return mapToResponse(invitation);
    }

    @Transactional
    public InvitationResponse decline(UUID invitationId, DeclineInvitationRequest request) {
        Invitation invitation = lockOpenInvitationOrThrow(invitationId);

        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());
        invitation = invitationRepository.save(invitation);

        eventPublisher.publishEvent(new InvitationDeclinedEvent(invitationId, invitation.getProjectId(), invitation.getInvitedUserId(), request.reason(), Instant.now()));

        return mapToResponse(invitation);
    }

    // ========================================================================
    // REVOCATION (Lead-initiated)
    // ========================================================================

    @Transactional
    public InvitationResponse revoke(UUID invitationId, UUID revokedBy, RevokeInvitationRequest request) {
        Invitation invitation = lockOpenInvitationOrThrow(invitationId);

        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRespondedAt(Instant.now());
        invitation = invitationRepository.save(invitation);

        eventPublisher.publishEvent(new InvitationRevokedEvent(invitationId, invitation.getProjectId(), invitation.getInvitedUserId(), revokedBy, request.reason(), Instant.now()));

        return mapToResponse(invitation);
    }

    /** Internal entry point for the expiration sweep. */
    @Transactional
    public void systemExpire(UUID invitationId) {
        Invitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Invitation not found."));

        if (invitation.getStatus() != InvitationStatus.SENT) {
            return; // Already responded to by the time the sweep runs.
        }

        invitation.setStatus(InvitationStatus.EXPIRED);
        invitationRepository.save(invitation);
        eventPublisher.publishEvent(new InvitationExpiredEvent(invitationId, invitation.getProjectId(), invitation.getInvitedUserId(), Instant.now()));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Invitation lockOpenInvitationOrThrow(UUID invitationId) {
        Invitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Invitation not found."));
        if (!invitation.isOpen()) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED,
                    "This invitation is already " + invitation.getStatus() + " and cannot be acted on again.");
        }
        return invitation;
    }

    private Invitation getInvitationOrThrow(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Invitation not found."));
    }

    private InvitationResponse mapToResponse(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getProjectId(),
                invitation.getInvitedUserId(),
                invitation.getInvitedBy(),
                invitation.getPreferredRole(),
                invitation.getMessage(),
                invitation.getStatus().name(),
                invitation.getRespondedAt(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt()
        );
    }
}
