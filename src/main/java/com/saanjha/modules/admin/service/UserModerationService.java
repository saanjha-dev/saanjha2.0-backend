package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.ModerationAction;
import com.saanjha.modules.admin.entity.ModerationActionType;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.event.AdminEvents.*;
import com.saanjha.modules.admin.repository.ModerationActionRepository;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.service.AuthAccountAdminService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * User Moderation. Admin never owns identity/account data — every mutation
 * here delegates to {@link AuthAccountAdminService}, the seam Auth exposes
 * for exactly this purpose (see that class's javadoc). This service's own
 * job is: record the {@link ModerationAction} + audit log entry, and publish
 * the resulting domain event. Authorization is enforced at the controller
 * layer via {@code @PreAuthorize("hasAuthority('admin:moderate')")}.
 *
 * "Never delete users" (Admin brief, USER MODERATION section) is enforced by
 * construction: no method on this class, nor on {@code AuthAccountAdminService},
 * ever calls {@code delete}/{@code deleteById}.
 */
@Service
@RequiredArgsConstructor
public class UserModerationService {

    private final AuthAccountAdminService authAccountAdminService;
    private final ModerationActionRepository moderationActionRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void warnUser(UUID actorId, UUID userId, String reason) {
        recordAction(ModerationActionType.USER_WARNED, actorId, userId, reason, null);
        auditService.record(actorId, "USER_WARNED", ModerationTargetType.USER, userId, null, null, reason);
        eventPublisher.publishEvent(new UserWarnedEvent(userId, actorId, reason, Instant.now()));
    }

    @Transactional
    public void suspendUser(UUID actorId, UUID userId, String reason, Instant expiresAt) {
        var before = authAccountAdminService.getAccountSummary(userId);
        authAccountAdminService.setAccountStatus(userId, AuthUser.AccountStatus.SUSPENDED);
        recordAction(ModerationActionType.USER_SUSPENDED, actorId, userId, reason, null);
        auditService.record(actorId, "USER_SUSPENDED", ModerationTargetType.USER, userId, before.status(), "SUSPENDED", reason);
        eventPublisher.publishEvent(new UserSuspendedEvent(userId, actorId, reason, expiresAt, Instant.now()));
    }

    @Transactional
    public void unsuspendUser(UUID actorId, UUID userId) {
        var before = authAccountAdminService.getAccountSummary(userId);
        authAccountAdminService.setAccountStatus(userId, AuthUser.AccountStatus.ACTIVE);
        recordAction(ModerationActionType.USER_UNSUSPENDED, actorId, userId, "Reinstated by administrator.", null);
        auditService.record(actorId, "USER_UNSUSPENDED", ModerationTargetType.USER, userId, before.status(), "ACTIVE", null);
        eventPublisher.publishEvent(new UserReinstatedEvent(userId, actorId, Instant.now()));
    }

    @Transactional
    public void banUser(UUID actorId, UUID userId, String reason) {
        var before = authAccountAdminService.getAccountSummary(userId);
        authAccountAdminService.setAccountStatus(userId, AuthUser.AccountStatus.BANNED);
        recordAction(ModerationActionType.USER_BANNED, actorId, userId, reason, null);
        auditService.record(actorId, "USER_BANNED", ModerationTargetType.USER, userId, before.status(), "BANNED", reason);
        eventPublisher.publishEvent(new UserBannedEvent(userId, actorId, reason, Instant.now()));
    }

    @Transactional
    public void unbanUser(UUID actorId, UUID userId) {
        var before = authAccountAdminService.getAccountSummary(userId);
        authAccountAdminService.setAccountStatus(userId, AuthUser.AccountStatus.ACTIVE);
        recordAction(ModerationActionType.USER_UNBANNED, actorId, userId, "Ban lifted by administrator.", null);
        auditService.record(actorId, "USER_UNBANNED", ModerationTargetType.USER, userId, before.status(), "ACTIVE", null);
        eventPublisher.publishEvent(new UserReinstatedEvent(userId, actorId, Instant.now()));
    }

    /**
     * Shadow ban: deliberately does NOT touch {@code AccountStatus} — the user
     * can still log in and use the platform normally, they are simply
     * de-prioritized/hidden in Discovery and other surfaces without being
     * told. Recorded here so the *decision* exists platform-wide, but real
     * enforcement (Discovery ranking, feed suppression) requires those
     * modules to consume {@link UserShadowBannedEvent} — documented as a
     * Future Extension Point, since Discovery does not have that listener
     * wired yet.
     */
    @Transactional
    public void shadowBanUser(UUID actorId, UUID userId, String reason) {
        recordAction(ModerationActionType.USER_SHADOW_BANNED, actorId, userId, reason, null);
        auditService.record(actorId, "USER_SHADOW_BANNED", ModerationTargetType.USER, userId, null, null, reason);
        eventPublisher.publishEvent(new UserShadowBannedEvent(userId, actorId, reason, Instant.now()));
    }

    @Transactional
    public void liftShadowBan(UUID actorId, UUID userId) {
        recordAction(ModerationActionType.USER_SHADOW_UNBANNED, actorId, userId, "Shadow ban lifted.", null);
        auditService.record(actorId, "USER_SHADOW_UNBANNED", ModerationTargetType.USER, userId, null, null, null);
    }

    @Transactional
    public void grantRole(UUID actorId, UUID userId, String roleName, String reason) {
        if ("ROLE_ADMIN".equals(roleName) && actorId.equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Administrators cannot self-escalate roles.");
        }
        authAccountAdminService.grantRole(userId, roleName);
        recordAction(ModerationActionType.USER_ROLE_GRANTED, actorId, userId, reason, roleName);
        auditService.record(actorId, "USER_ROLE_GRANTED", ModerationTargetType.USER, userId, null, roleName, reason);
        eventPublisher.publishEvent(new UserRoleChangedEvent(userId, actorId, roleName, true, Instant.now()));
    }

    @Transactional
    public void revokeRole(UUID actorId, UUID userId, String roleName, String reason) {
        if ("ROLE_ADMIN".equals(roleName) && actorId.equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Administrators cannot revoke their own admin role.");
        }
        authAccountAdminService.revokeRole(userId, roleName);
        recordAction(ModerationActionType.USER_ROLE_REVOKED, actorId, userId, reason, roleName);
        auditService.record(actorId, "USER_ROLE_REVOKED", ModerationTargetType.USER, userId, roleName, null, reason);
        eventPublisher.publishEvent(new UserRoleChangedEvent(userId, actorId, roleName, false, Instant.now()));
    }

    private void recordAction(ModerationActionType type, UUID actorId, UUID userId, String reason, String evidence) {
        ModerationAction action = new ModerationAction();
        action.setTargetType(ModerationTargetType.USER);
        action.setTargetId(userId);
        action.setActionType(type);
        action.setActorId(actorId);
        action.setReason(reason);
        action.setEvidence(evidence);
        moderationActionRepository.save(action);
    }
}
