package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.ModerationAction;
import com.saanjha.modules.admin.entity.ModerationActionType;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.entity.ProjectModerationOverlay;
import com.saanjha.modules.admin.event.AdminEvents.*;
import com.saanjha.modules.admin.repository.ModerationActionRepository;
import com.saanjha.modules.admin.repository.ProjectModerationOverlayRepository;
import com.saanjha.modules.project.dto.ProjectRequestDTOs.UpdateProjectStatusRequest;
import com.saanjha.modules.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Project Moderation. Split deliberately in two:
 *
 * <ul>
 *   <li><b>Lock / Hide / Feature</b> — pure governance overlay, owned
 *   entirely by Admin (see {@link ProjectModerationOverlay}'s javadoc for
 *   why this does not live in Project's own schema or state machine).</li>
 *   <li><b>Remove (Archive)</b> — reuses Project's own, already-validated
 *   {@code ARCHIVED} terminal state via {@code ProjectService.transitionStatus},
 *   which Project's {@code project:moderate} authority (already granted to
 *   ROLE_ADMIN, see V6 migration) already permits. Admin does not
 *   reimplement archival; it only records the governance decision and
 *   audit trail around a call Project already exposes and enforces.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProjectModerationService {

    private final ProjectService projectService;
    private final ProjectModerationOverlayRepository overlayRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void lockProject(UUID actorId, UUID projectId, String reason) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setLocked(true);
        overlay.setLockedReason(reason);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_LOCKED, actorId, projectId, reason);
        auditService.record(actorId, "PROJECT_LOCKED", ModerationTargetType.PROJECT, projectId, "false", "true", reason);
        eventPublisher.publishEvent(new ProjectLockedEvent(projectId, actorId, reason, Instant.now()));
    }

    @Transactional
    public void unlockProject(UUID actorId, UUID projectId) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setLocked(false);
        overlay.setLockedReason(null);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_UNLOCKED, actorId, projectId, "Unlocked by administrator.");
        auditService.record(actorId, "PROJECT_UNLOCKED", ModerationTargetType.PROJECT, projectId, "true", "false", null);
        eventPublisher.publishEvent(new ProjectUnlockedEvent(projectId, actorId, Instant.now()));
    }

    @Transactional
    public void hideProject(UUID actorId, UUID projectId, String reason) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setHidden(true);
        overlay.setHiddenReason(reason);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_HIDDEN, actorId, projectId, reason);
        auditService.record(actorId, "PROJECT_HIDDEN", ModerationTargetType.PROJECT, projectId, "false", "true", reason);
        eventPublisher.publishEvent(new ProjectHiddenEvent(projectId, actorId, reason, Instant.now()));
    }

    @Transactional
    public void unhideProject(UUID actorId, UUID projectId) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setHidden(false);
        overlay.setHiddenReason(null);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_UNHIDDEN, actorId, projectId, "Unhidden by administrator.");
        auditService.record(actorId, "PROJECT_UNHIDDEN", ModerationTargetType.PROJECT, projectId, "true", "false", null);
        eventPublisher.publishEvent(new ProjectUnhiddenEvent(projectId, actorId, Instant.now()));
    }

    @Transactional
    public void featureProject(UUID actorId, UUID projectId) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setFeatured(true);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_FEATURED, actorId, projectId, "Featured by administrator.");
        auditService.record(actorId, "PROJECT_FEATURED", ModerationTargetType.PROJECT, projectId, "false", "true", null);
        eventPublisher.publishEvent(new ProjectFeaturedEvent(projectId, actorId, Instant.now()));
    }

    @Transactional
    public void unfeatureProject(UUID actorId, UUID projectId) {
        ProjectModerationOverlay overlay = getOrCreateOverlay(projectId);
        overlay.setFeatured(false);
        overlay.setUpdatedBy(actorId);
        overlayRepository.save(overlay);

        recordAction(ModerationActionType.PROJECT_UNFEATURED, actorId, projectId, "Unfeatured by administrator.");
        auditService.record(actorId, "PROJECT_UNFEATURED", ModerationTargetType.PROJECT, projectId, "true", "false", null);
        eventPublisher.publishEvent(new ProjectUnfeaturedEvent(projectId, actorId, Instant.now()));
    }

    /** Reuses Project's own ARCHIVED terminal transition — see class javadoc. */
    @Transactional
    public void removeProject(UUID actorId, UUID projectId, String reason) {
        projectService.transitionStatus(projectId, actorId, new UpdateProjectStatusRequest("ARCHIVED", reason));

        recordAction(ModerationActionType.PROJECT_ARCHIVED_BY_ADMIN, actorId, projectId, reason);
        auditService.record(actorId, "PROJECT_ARCHIVED_BY_ADMIN", ModerationTargetType.PROJECT, projectId, null, "ARCHIVED", reason);
        eventPublisher.publishEvent(new ProjectRemovedByAdminEvent(projectId, actorId, reason, Instant.now()));
    }

    private ProjectModerationOverlay getOrCreateOverlay(UUID projectId) {
        return overlayRepository.findByProjectId(projectId).orElseGet(() -> {
            ProjectModerationOverlay overlay = new ProjectModerationOverlay();
            overlay.setProjectId(projectId);
            return overlay;
        });
    }

    private void recordAction(ModerationActionType type, UUID actorId, UUID projectId, String reason) {
        ModerationAction action = new ModerationAction();
        action.setTargetType(ModerationTargetType.PROJECT);
        action.setTargetId(projectId);
        action.setActionType(type);
        action.setActorId(actorId);
        action.setReason(reason);
        moderationActionRepository.save(action);
    }
}
