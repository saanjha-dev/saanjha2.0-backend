package com.saanjha.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.shared.api.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter-chain-level counterpart to {@link RestAuthenticationEntryPoint}:
 * covers an {@link AccessDeniedException} thrown by Spring Security's
 * {@code AuthorizationFilter} for an already-authenticated caller who fails
 * a URL-based rule (e.g. a future {@code .hasRole(...)} matcher in {@code
 * SecurityConfig}). Today, authorization in this codebase is enforced at the
 * method-security layer ({@code @PreAuthorize}, via
 * {@code @EnableMethodSecurity}), where an {@link AccessDeniedException} is
 * thrown during normal controller invocation and IS caught by {@code
 * GlobalExceptionHandler}'s {@code @ExceptionHandler(AccessDeniedException.class)}
 * - this handler exists so the same exception type produces the same
 * {@code ApiEnvelope} shape regardless of which layer rejects the request,
 * rather than leaving a filter-level gap for whoever adds the first
 * URL-based role rule.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("Rejected request to {} {}: {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiEnvelope.failure(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.getDefaultMessage(), null)));
    }
}
