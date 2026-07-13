package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.entity.AuthRole;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.repository.AuthRoleRepository;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * FEATURE (Admin module integration point).
 *
 * The public seam Auth exposes specifically for Platform Governance: Admin
 * injects and calls this service (a plain Spring bean, same JVM) rather than
 * reaching into {@code AuthUserRepository}/{@code AuthUser} directly —
 * consistent with the boundary rule ("cross-module calls go through service
 * interfaces ... only", never another module's JPA repository).
 *
 * Auth remains the sole owner of {@code AccountStatus} and of what it means
 * for login (see {@code AuthService.login}); this class only exposes the
 * narrow set of mutations Admin legitimately needs to trigger, each of which
 * also revokes all live sessions, so a suspension or ban takes effect
 * immediately rather than waiting for the next login.
 */
@Service
@RequiredArgsConstructor
public class AuthAccountAdminService {

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthSessionRepository sessionRepository;

    @Transactional
    public void setAccountStatus(UUID userId, AuthUser.AccountStatus status) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        userRepository.updateAccountStatus(userId, status);
        if (status != AuthUser.AccountStatus.ACTIVE) {
            // Force logout everywhere — a moderation action must take effect immediately,
            // not at the next natural token expiry. Reuses the exact mechanism already
            // used for "Logout Everywhere" and for refresh-token-reuse revocation.
            sessionRepository.deactivateAllUserSessions(userId);
        }
    }

    @Transactional(readOnly = true)
    public AccountSummary getAccountSummary(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        return new AccountSummary(
                user.getId(), user.getEmail(), user.getStatus().name(), user.isEmailVerified(),
                user.getRoles().stream().map(AuthRole::getName).toList()
        );
    }

    @Transactional
    public void grantRole(UUID userId, String roleName) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        AuthRole role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Role not found: " + roleName));
        user.getRoles().add(role);
        userRepository.save(user);
    }

    @Transactional
    public void revokeRole(UUID userId, String roleName) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        AuthRole role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Role not found: " + roleName));
        user.getRoles().remove(role);
        userRepository.save(user);
    }

    public record AccountSummary(UUID userId, String email, String status, boolean emailVerified, java.util.List<String> roles) {}
}
