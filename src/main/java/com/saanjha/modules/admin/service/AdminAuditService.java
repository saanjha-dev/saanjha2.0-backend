package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.AdminAuditLog;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.AdminAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Central, mandatory recorder for the technical audit ledger. Every service
 * in this module calls {@link #record} immediately after (in the same
 * transaction as) any mutating action — "no admin action should ever be
 * invisible" (Admin brief, AUDIT section). This is a plain service method,
 * not an AOP aspect: the codebase has no aspect infrastructure today (no
 * spring-boot-starter-aop dependency), and an explicit call at each mutation
 * site is more auditable on its own terms — a reviewer can see, at the call
 * site, exactly what is and isn't being logged, rather than trusting a
 * pointcut expression to have matched correctly.
 *
 * Deliberately {@code Propagation.MANDATORY}: this must only ever be called
 * from inside an already-open transaction (the same one performing the
 * mutation), so an audit-log failure can never silently succeed while the
 * governance action itself is lost, or vice versa.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, ModerationTargetType targetType, UUID targetId,
                        String oldValue, String newValue, String reason) {
        // OBSERVABILITY (Admin brief): every audited action increments a tagged
        // counter, giving "audit metrics" / "moderation metrics" / "ban metrics"
        // etc. for free from one call site, rather than one counter per service.
        meterRegistry.counter("admin.audit.actions", "action", action).increment();

        AdminAuditLog log = new AdminAuditLog();
        log.setActorId(actorId);
        log.setActorRoles(currentActorRoles());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setReason(reason);
        log.setRequestId(currentRequestId());
        log.setIpAddress(currentIp());
        log.setUserAgent(currentUserAgent());
        auditLogRepository.save(log);
    }

    private String currentActorRoles() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().toString();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private String currentRequestId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }

    /**
     * FIX (hardening sprint, P0-4): previously trusted the raw, client-
     * supplied {@code X-Forwarded-For} header outright. Same root cause
     * already found and fixed in {@code RateLimitAspect} - this repository
     * has no reverse proxy in front of it anywhere (confirmed via
     * repository-wide search: no nginx config, no
     * {@code server.forward-headers-strategy}), so nothing was overwriting
     * that header for a direct caller. Here the consequence is worse than a
     * rate-limit bypass: this value is written into the permanent,
     * append-only accountability ledger the brief calls "the technical audit
     * ledger" - any actor could inject an arbitrary false IP into their own
     * audit record, undermining exactly the forensic guarantee this table
     * exists to provide. Now uses only {@code request.getRemoteAddr()}; see
     * {@code RateLimitAspect.getClientIdentifier()}'s javadoc for the same
     * reasoning and the path to a real trusted-proxy configuration later.
     */
    private String currentIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }
}
