package com.saanjha.modules.auth.service;

import com.saanjha.modules.auth.dto.RequestDTOs.LoginRequest;
import com.saanjha.modules.auth.dto.RequestDTOs.RegisterRequest;
import com.saanjha.modules.auth.dto.RequestDTOs.VerifyOtpRequest;
import com.saanjha.modules.auth.dto.RequestDTOs.VerifyResetOtpRequest;
import com.saanjha.modules.auth.dto.ResponseDTOs;
import com.saanjha.modules.auth.entity.AuthRole;
import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.repository.AuthRoleRepository;
import com.saanjha.modules.auth.repository.AuthSessionRepository;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.repository.RefreshTokenRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Zero prior coverage for this class before the hardening sprint (grep
 * confirmed no existing test file). Focused on the business rules and the
 * three P0-3 fixes made to this class: the login timing side-channel, the
 * per-target OTP attempt limiter, and the removed debug logging (that last
 * one has no behavior to test, just confirmed absent by reading the diff).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserRepository userRepository;
    @Mock private AuthRoleRepository roleRepository;
    @Mock private AuthSessionRepository sessionRepository;
    @Mock private RefreshTokenRepository tokenRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenRotationService tokenRotationService;
    @Mock private EventPublisherService eventPublisher;
    @Mock private PermissionCacheService permissionCacheService;
    @Mock private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, sessionRepository, tokenRepository,
                redisTemplate, passwordEncoder, tokenRotationService, eventPublisher, permissionCacheService, jwtProvider);
    }

    // ------------------------------------------------------------------
    // register()
    // ------------------------------------------------------------------

    @Test
    void register_existingEmail_throwsConflict() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@example.com", "password123")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_newEmail_savesUserAndDispatchesOtp() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new AuthRole()));
        // encode() is called for both the real password AND the random OTP -
        // stub generically rather than pinning to one exact argument.
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.register(new RegisterRequest("new@example.com", "password123"));

        verify(userRepository).saveAndFlush(argThat(u ->
                u.getEmail().equals("new@example.com") && !u.isEmailVerified()));
        verify(valueOperations).set(eq("auth:otp:EMAIL_VERIFICATION:new@example.com"), anyString(), eq(Duration.ofMinutes(5)));
        verify(eventPublisher).publish(any());
    }

    @Test
    void register_missingDefaultRole_throwsInternalError() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(new RegisterRequest("new@example.com", "password123")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // ------------------------------------------------------------------
    // login() - including the P0-3 timing-mitigation fix
    // ------------------------------------------------------------------

    @Test
    void login_nonExistentEmail_stillRunsAPasswordComparison_forTimingSafety() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("whatever"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever", "device-1"), "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        // The whole point of the fix: exactly one bcrypt comparison still
        // happens even though there's no real user to compare against -
        // otherwise this path would be measurably faster than the
        // wrong-password path below, leaking account existence via timing.
        verify(passwordEncoder).matches(eq("whatever"), anyString());
    }

    @Test
    void login_wrongPassword_isRejectedWithTheSameGenericMessageAsNonExistentEmail() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong-password", "device-1"), "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid credentials.");
                });
    }

    @Test
    void login_unverifiedEmail_isRejected() {
        AuthUser user = activeVerifiedUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "correct", "device-1"), "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void login_lockedAccount_returnsAccountLockedCode() {
        AuthUser user = activeVerifiedUser();
        user.setStatus(AuthUser.AccountStatus.LOCKED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "correct", "device-1"), "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    void login_bannedAccount_returnsAccountSuspendedCode() {
        AuthUser user = activeVerifiedUser();
        user.setStatus(AuthUser.AccountStatus.BANNED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "correct", "device-1"), "1.2.3.4"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_SUSPENDED));
    }

    @Test
    void login_validCredentials_createsSessionAndIssuesTokenFamily() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);
        ResponseDTOs.AuthTokens expectedTokens = new ResponseDTOs.AuthTokens("access", "refresh", 900_000L);
        when(tokenRotationService.createTokenFamily(any(), eq(user))).thenReturn(expectedTokens);

        ResponseDTOs.AuthTokens result = authService.login(new LoginRequest(user.getEmail(), "correct", "device-1"), "1.2.3.4");

        assertThat(result).isEqualTo(expectedTokens);
        verify(sessionRepository).save(argThat(session ->
                session.getUserId().equals(user.getId())
                        && session.getDeviceId().equals("device-1")
                        && session.getDeviceIp().equals("1.2.3.4")));
    }

    // ------------------------------------------------------------------
    // requestPasswordReset() - no user enumeration via a thrown exception
    // ------------------------------------------------------------------

    @Test
    void requestPasswordReset_unknownEmail_isSilentNoOp() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("ghost@example.com");

        verifyNoInteractions(redisTemplate, eventPublisher);
    }

    @Test
    void requestPasswordReset_nonActiveAccount_isSilentNoOp() {
        AuthUser user = activeVerifiedUser();
        user.setStatus(AuthUser.AccountStatus.SUSPENDED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        authService.requestPasswordReset(user.getEmail());

        verifyNoInteractions(redisTemplate, eventPublisher);
    }

    @Test
    void requestPasswordReset_activeAccount_dispatchesOtp() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");

        authService.requestPasswordReset(user.getEmail());

        verify(valueOperations).set(eq("auth:otp:PASSWORD_RESET:" + user.getEmail()), anyString(), eq(Duration.ofMinutes(5)));
    }

    // ------------------------------------------------------------------
    // resetPassword() - must invalidate every existing session (P0-3 review: confirmed correct)
    // ------------------------------------------------------------------

    @Test
    void resetPassword_success_updatesPasswordAndRevokesAllSessions() {
        UUID userId = UUID.randomUUID();
        AuthUser user = activeVerifiedUser();
        user.setId(userId);
        when(jwtProvider.validatePasswordResetToken("reset-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

        authService.resetPassword("reset-token", "newPassword123");

        verify(userRepository).updatePassword(userId, "new-hash");
        verify(sessionRepository).deactivateAllUserSessions(userId);
        verify(tokenRepository).revokeAllTokensForUser(userId);
        verify(permissionCacheService).evictUserCache(userId);
    }

    @Test
    void resetPassword_userNoLongerExists_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.validatePasswordResetToken("reset-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("reset-token", "newPassword123"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userRepository, never()).updatePassword(any(), any());
    }

    // ------------------------------------------------------------------
    // OTP attempt limiting (P0-3 fix) via verifyEmail()
    // ------------------------------------------------------------------

    @Test
    void verifyEmail_correctOtp_verifiesAndPublishesEvent() {
        AuthUser user = activeVerifiedUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:otp-attempts:EMAIL_VERIFICATION:" + user.getEmail())).thenReturn(1L);
        when(valueOperations.get("auth:otp:EMAIL_VERIFICATION:" + user.getEmail())).thenReturn("hashed-otp");
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);

        authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "123456"));

        verify(userRepository).markEmailAsVerified(user.getId());
        verify(eventPublisher).publish(any());
        verify(redisTemplate).delete("auth:otp:EMAIL_VERIFICATION:" + user.getEmail());
        verify(redisTemplate).delete("auth:otp-attempts:EMAIL_VERIFICATION:" + user.getEmail());
    }

    @Test
    void verifyEmail_wrongOtp_isRejectedWithoutVerifying() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("auth:otp:EMAIL_VERIFICATION:" + user.getEmail())).thenReturn("hashed-otp");
        when(passwordEncoder.matches("000000", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "000000")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userRepository, never()).markEmailAsVerified(any());
    }

    @Test
    void verifyEmail_noOtpEverIssued_isRejected() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("auth:otp:EMAIL_VERIFICATION:" + user.getEmail())).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "123456")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    /**
     * FIX (P0-3): proves the new per-target attempt limiter actually blocks
     * further guesses once the threshold is crossed, regardless of which IP
     * or identity is making the call - this is the exact gap that let a
     * distributed attacker brute-force one victim's OTP across many source
     * IPs, each with its own separate (and therefore useless) IP-based
     * @RateLimit bucket.
     */
    @Test
    void verifyEmail_exceedsMaxAttempts_isRejectedWithoutEverCheckingTheOtpValue() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 6th attempt for this email+purpose - already over the MAX_OTP_ATTEMPTS=5 threshold.
        when(valueOperations.increment("auth:otp-attempts:EMAIL_VERIFICATION:" + user.getEmail())).thenReturn(6L);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "123456")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        // Never even looks at the stored OTP once the attempt budget is blown -
        // proves the limiter short-circuits before the comparison, not after.
        verify(valueOperations, never()).get("auth:otp:EMAIL_VERIFICATION:" + user.getEmail());
    }

    @Test
    void verifyPasswordResetOtp_correctOtp_returnsResetTokenWithFiveMinuteExpiry() {
        AuthUser user = activeVerifiedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("auth:otp:PASSWORD_RESET:" + user.getEmail())).thenReturn("hashed-otp");
        when(passwordEncoder.matches("654321", "hashed-otp")).thenReturn(true);
        when(jwtProvider.generatePasswordResetToken(user.getId(), user.getEmail())).thenReturn("reset-jwt");

        ResponseDTOs.PasswordResetTokenResponse response =
                authService.verifyPasswordResetOtp(new VerifyResetOtpRequest(user.getEmail(), "654321"));

        assertThat(response.resetToken()).isEqualTo("reset-jwt");
        assertThat(response.expiresIn()).isEqualTo(300);
    }

    // ------------------------------------------------------------------
    // logout
    // ------------------------------------------------------------------

    @Test
    void logoutCurrentSession_deactivatesSessionAndRevokesItsTokens() {
        UUID sessionId = UUID.randomUUID();
        when(tokenRotationService.extractSessionIdFromToken("raw-refresh")).thenReturn(sessionId);

        authService.logoutCurrentSession("raw-refresh", "device-1");

        verify(sessionRepository).deactivateSession(sessionId);
        verify(tokenRepository).revokeAllTokensForSession(sessionId);
    }

    @Test
    void logoutAllDevices_wrongPassword_isRejectedAndNothingIsRevoked() {
        UUID userId = UUID.randomUUID();
        AuthUser user = activeVerifiedUser();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.logoutAllDevices(userId, "wrong"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sessionRepository, never()).deactivateAllUserSessions(any());
    }

    @Test
    void logoutAllDevices_correctPassword_revokesEverything() {
        UUID userId = UUID.randomUUID();
        AuthUser user = activeVerifiedUser();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);

        authService.logoutAllDevices(userId, "correct");

        verify(sessionRepository).deactivateAllUserSessions(userId);
        verify(tokenRepository).revokeAllTokensForUser(userId);
    }

    private AuthUser activeVerifiedUser() {
        AuthUser user = new AuthUser();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("$2a$10$realHashValueGoesHere");
        user.setEmailVerified(true);
        user.setStatus(AuthUser.AccountStatus.ACTIVE);
        return user;
    }
}
