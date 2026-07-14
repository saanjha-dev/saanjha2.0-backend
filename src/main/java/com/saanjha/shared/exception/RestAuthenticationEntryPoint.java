package com.saanjha.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.shared.api.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * FIX (hardening sprint, P0-2): {@code GlobalExceptionHandler} is a
 * {@code @RestControllerAdvice}, which only intercepts exceptions thrown
 * during normal Spring MVC dispatch (i.e. inside a controller method, or
 * while Spring MVC resolves its arguments/return value). An anonymous
 * request hitting an {@code .authenticated()} route never gets that far:
 * Spring Security's {@code AuthorizationFilter} rejects it in the filter
 * chain, before {@code DispatcherServlet} runs, and {@code
 * ExceptionTranslationFilter} hands the failure to whatever {@code
 * AuthenticationEntryPoint} is configured. {@code SecurityConfig} never
 * configured one, so Spring Security fell back to its own default
 * ({@code Http403ForbiddenEntryPoint}) - a bare, bodyless 403 with no
 * {@code ApiEnvelope}, and the wrong status code (401 is correct here, not
 * 403: the caller has no credentials at all, as opposed to valid credentials
 * lacking sufficient permission - see {@link RestAccessDeniedHandler} for
 * that latter case). Confirmed via grep: no {@code AuthenticationEntryPoint}
 * or {@code exceptionHandling(...)} existed anywhere in the codebase before
 * this fix, and nothing in this codebase throws
 * {@code AuthenticationException} from inside a controller call stack
 * (login manually compares password hashes and throws {@link AppException},
 * never touches Spring Security's {@code AuthenticationManager}) - so this
 * filter-level entry point is the ONLY place a 401 for "no/invalid session"
 * can be produced consistently; a {@code @ExceptionHandler
 * (AuthenticationException.class)} in {@code GlobalExceptionHandler} would
 * have been unreachable dead code for this codebase's actual auth flow.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.warn("Rejected unauthenticated request to {} {}: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiEnvelope.failure(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getDefaultMessage(), null)));
    }
}
