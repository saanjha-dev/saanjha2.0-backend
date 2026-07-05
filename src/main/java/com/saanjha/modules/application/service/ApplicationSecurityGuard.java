package com.saanjha.modules.application.service;

import com.saanjha.modules.application.entity.Invitation;
import com.saanjha.modules.application.entity.ProjectApplication;
import com.saanjha.modules.application.repository.InvitationRepository;
import com.saanjha.modules.application.repository.ProjectApplicationRepository;
import com.saanjha.modules.project.service.ProjectSecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resource-level authorization guard for the Application module.
 *
 * Deliberately composes {@code ProjectSecurityGuard} rather than
 * re-implementing "is this user the project's Lead" — that check is owned by
 * the Project module and must have exactly one implementation. This guard
 * only adds what's specific to Application/Invitation: "is this user the
 * applicant/invitee themself", plus the one extra hop of "load the
 * application, then ask Project's guard about its projectId".
 */
@Component("applicationGuard")
@RequiredArgsConstructor
public class ApplicationSecurityGuard {

    private final ProjectApplicationRepository applicationRepository;
    private final InvitationRepository invitationRepository;
    private final ProjectSecurityGuard projectSecurityGuard;

    /** True if the given user is the applicant who owns this application. */
    public boolean isApplicant(UUID applicationId, String userIdText) {
        if (applicationId == null || userIdText == null) {
            return false;
        }
        return applicationRepository.findById(applicationId)
                .map(app -> app.getApplicantId().toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }

    /** True if the given user leads the project this application was submitted to. */
    public boolean isReviewerOfApplication(UUID applicationId, String userIdText) {
        if (applicationId == null || userIdText == null) {
            return false;
        }
        return applicationRepository.findById(applicationId)
                .map(ProjectApplication::getProjectId)
                .map(projectId -> projectSecurityGuard.isLead(projectId, userIdText))
                .orElse(false);
    }

    /** True if the given user is the invitee this invitation was sent to. */
    public boolean isInvitee(UUID invitationId, String userIdText) {
        if (invitationId == null || userIdText == null) {
            return false;
        }
        return invitationRepository.findById(invitationId)
                .map(inv -> inv.getInvitedUserId().toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }

    /** True if the given user leads the project this invitation was sent for. */
    public boolean isSenderOfInvitation(UUID invitationId, String userIdText) {
        if (invitationId == null || userIdText == null) {
            return false;
        }
        return invitationRepository.findById(invitationId)
                .map(Invitation::getProjectId)
                .map(projectId -> projectSecurityGuard.isLead(projectId, userIdText))
                .orElse(false);
    }
}
