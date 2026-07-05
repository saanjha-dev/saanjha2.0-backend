package com.saanjha.modules.project.service;

import com.saanjha.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resource-level authorization guard for the Project module, invoked from
 * controller @PreAuthorize SpEL expressions (e.g.
 * {@code @projectGuard.isLead(#id, authentication.name)}).
 *
 * TEAM_LEAD in the platform's security matrix is a per-resource role, not a
 * global auth role — a user is only "lead" in the context of one specific
 * project. That contextual check belongs here, not in the global RBAC layer.
 */
@Component("projectGuard")
@RequiredArgsConstructor
public class ProjectSecurityGuard {

    private final ProjectRepository projectRepository;

    /**
     * @param projectId  the project being acted on
     * @param userIdText the authenticated principal's user id, as injected by
     *                   JwtAuthenticationFilter (authentication.getName(), a UUID string)
     * @return true if the given user is the project's Lead. Returns false
     *         (never throws) for a missing project, deferring the actual
     *         404 to the service layer so authorization and not-found are
     *         not conflated.
     */
    public boolean isLead(UUID projectId, String userIdText) {
        if (projectId == null || userIdText == null) {
            return false;
        }
        return projectRepository.findById(projectId)
                .map(project -> project.getLeadUserId().toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }
}
