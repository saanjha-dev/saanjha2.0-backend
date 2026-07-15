package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.AdminAuditLog;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.AdminAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * FIX (hardening sprint, P0-4): {@code currentIp()} previously trusted the
 * raw, client-supplied {@code X-Forwarded-For} header - since this
 * repository has no reverse proxy in front of it anywhere, any actor could
 * inject an arbitrary false IP into their own permanent audit trail record.
 * This proves the fix: a spoofed X-Forwarded-For header is now ignored
 * entirely, and the real remote address is recorded instead.
 */
class AdminAuditServiceTest {

    private AdminAuditLogRepository auditLogRepository;
    private AdminAuditService auditService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AdminAuditLogRepository.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        auditService = new AdminAuditService(auditLogRepository, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void record_ignoresSpoofedXForwardedForHeader_usesRealRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.99"); // the real TCP peer
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // attacker-supplied, must be ignored
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        auditService.record(actorId, "USER_BANNED", ModerationTargetType.USER, targetId, "ACTIVE", "BANNED", "test");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getIpAddress())
                .as("must record the real peer address, never the spoofable X-Forwarded-For value")
                .isEqualTo("203.0.113.99");
    }

    @Test
    void record_withNoRequestContext_doesNotThrow() {
        // e.g. an audited action triggered from a scheduled job / async listener,
        // not a live HTTP request - must degrade gracefully, not NPE.
        RequestContextHolder.resetRequestAttributes();

        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        auditService.record(actorId, "USER_WARNED", ModerationTargetType.USER, targetId, null, null, "test");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
    }

    @Test
    void record_capturesAllFieldsPassedIn() {
        RequestContextHolder.resetRequestAttributes();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        auditService.record(actorId, "USER_BANNED", ModerationTargetType.USER, targetId, "ACTIVE", "BANNED", "spam");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AdminAuditLog saved = captor.getValue();

        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo("USER_BANNED");
        assertThat(saved.getTargetType()).isEqualTo(ModerationTargetType.USER);
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getOldValue()).isEqualTo("ACTIVE");
        assertThat(saved.getNewValue()).isEqualTo("BANNED");
        assertThat(saved.getReason()).isEqualTo("spam");
    }
}
