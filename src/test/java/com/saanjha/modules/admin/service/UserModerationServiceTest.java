package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.ModerationActionRepository;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.service.AuthAccountAdminService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The self-escalation guards in this class (an admin can't grant/revoke
 * their own ROLE_ADMIN) are the single highest-value authorization control
 * in the Admin module - this suite exists specifically to make sure they
 * stay in place through future refactors, plus confirms every action
 * correctly writes both a ModerationAction row and an AdminAuditLog entry
 * (the "no admin action should ever be invisible" requirement).
 */
@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    @Mock private AuthAccountAdminService authAccountAdminService;
    @Mock private ModerationActionRepository moderationActionRepository;
    @Mock private AdminAuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserModerationService userModerationService;

    @BeforeEach
    void setUp() {
        userModerationService = new UserModerationService(
                authAccountAdminService, moderationActionRepository, auditService, eventPublisher);
    }

    @Test
    void grantRole_adminGrantingSelfRoleAdmin_isForbidden() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> userModerationService.grantRole(adminId, adminId, "ROLE_ADMIN", "reason"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(authAccountAdminService, auditService, eventPublisher);
    }

    @Test
    void revokeRole_adminRevokingOwnRoleAdmin_isForbidden() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> userModerationService.revokeRole(adminId, adminId, "ROLE_ADMIN", "reason"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(authAccountAdminService, auditService, eventPublisher);
    }

    @Test
    void grantRole_adminGrantingSelfADifferentRole_isAllowed() {
        UUID adminId = UUID.randomUUID();

        userModerationService.grantRole(adminId, adminId, "ROLE_MODERATOR", "self-assigned during setup");

        verify(authAccountAdminService).grantRole(adminId, "ROLE_MODERATOR");
        verify(auditService).record(eq(adminId), eq("USER_ROLE_GRANTED"), eq(ModerationTargetType.USER), eq(adminId), any(), eq("ROLE_MODERATOR"), any());
    }

    @Test
    void grantRole_adminGrantingRoleAdminToSomeoneElse_isAllowed() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        userModerationService.grantRole(actorId, targetId, "ROLE_ADMIN", "promoting to admin");

        verify(authAccountAdminService).grantRole(targetId, "ROLE_ADMIN");
        verify(auditService).record(eq(actorId), eq("USER_ROLE_GRANTED"), eq(ModerationTargetType.USER), eq(targetId), any(), eq("ROLE_ADMIN"), any());
    }

    @Test
    void banUser_recordsModerationActionAuditEntryAndEvent() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(authAccountAdminService.getAccountSummary(targetId))
                .thenReturn(new AuthAccountAdminService.AccountSummary(targetId, "user@example.com", "ACTIVE", true, List.of()));

        userModerationService.banUser(actorId, targetId, "repeated ToS violations");

        verify(authAccountAdminService).setAccountStatus(targetId, AuthUser.AccountStatus.BANNED);
        verify(moderationActionRepository).save(any());
        verify(auditService).record(eq(actorId), eq("USER_BANNED"), eq(ModerationTargetType.USER), eq(targetId), eq("ACTIVE"), eq("BANNED"), eq("repeated ToS violations"));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void suspendUser_recordsModerationActionAuditEntryAndEvent() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(authAccountAdminService.getAccountSummary(targetId))
                .thenReturn(new AuthAccountAdminService.AccountSummary(targetId, "user@example.com", "ACTIVE", true, List.of()));

        userModerationService.suspendUser(actorId, targetId, "spam", null);

        verify(authAccountAdminService).setAccountStatus(targetId, AuthUser.AccountStatus.SUSPENDED);
        verify(auditService).record(eq(actorId), eq("USER_SUSPENDED"), eq(ModerationTargetType.USER), eq(targetId), eq("ACTIVE"), eq("SUSPENDED"), eq("spam"));
    }

    @Test
    void warnUser_recordsModerationActionAndAuditEntry_withoutTouchingAccountStatus() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        userModerationService.warnUser(actorId, targetId, "first warning");

        verifyNoInteractions(authAccountAdminService);
        verify(moderationActionRepository).save(any());
        verify(auditService).record(eq(actorId), eq("USER_WARNED"), eq(ModerationTargetType.USER), eq(targetId), any(), any(), eq("first warning"));
    }
}
