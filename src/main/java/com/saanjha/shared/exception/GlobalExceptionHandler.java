package com.saanjha.shared.exception;

import com.saanjha.shared.api.ApiEnvelope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAppException(AppException ex) {
        if (ex.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("Internal domain error: ", ex);
        } else {
            log.warn("Domain rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiEnvelope.failure(ex.getErrorCode().name(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Payload validation failed: {}", validationErrors);

        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.VALIDATION_FAILED.name(),
                        "Request payload failed structural validation.",
                        validationErrors
                ));
    }

    /**
     * FIX (hardening sprint, P0-2): {@link MethodArgumentNotValidException}
     * (handled above) is itself a subtype of {@link BindException} - Spring
     * always dispatches to the most specific registered handler, so this one
     * only ever actually fires for the OTHER thing that throws a bare {@code
     * BindException}: a {@code @ModelAttribute}-bound object (e.g. GET
     * request query params bound to a DTO) failing {@code @Valid}, which
     * doesn't go through the same code path as an {@code @RequestBody} JSON
     * payload. Previously fell through to the generic 500 handler, which is
     * both the wrong status (this is a client input problem, not a server
     * fault) and hid the field-level detail the caller needs to fix their
     * request.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBindException(BindException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Request parameter binding/validation failed: {}", validationErrors);

        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.VALIDATION_FAILED.name(),
                        "Request parameters failed validation.",
                        validationErrors
                ));
    }

    /**
     * Thrown by Bean Validation for constraints Spring's own MVC validation
     * doesn't intercept the same way - {@code @Validated} on a
     * {@code @RequestParam}/{@code @PathVariable} at the controller method
     * level, or a JPA entity failing its own {@code @NotNull}/{@code @Size}
     * etc. at flush time. Previously fell through to the generic 500
     * handler even though this is squarely a client input problem.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            validationErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        log.warn("Constraint validation failed: {}", validationErrors);

        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.VALIDATION_FAILED.name(),
                        "Request failed validation constraints.",
                        validationErrors
                ));
    }

    /**
     * Malformed/unreadable request body - not valid JSON at all, wrong
     * content type parsed as JSON, or a type mismatch Jackson can't coerce
     * (e.g. a string where a number is expected). This is a client mistake,
     * not a server fault, but the underlying Jackson exception message can
     * include internal field/class names, so only a generic message is ever
     * returned to the caller - full detail is logged server-side only.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable/malformed request body: {}", ex.getMessage());

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.BAD_REQUEST.name(),
                        "The request body is missing or could not be parsed.",
                        null
                ));
    }

    /**
     * An authenticated caller failed a {@code @PreAuthorize} check during
     * controller invocation - this codebase's actual authorization
     * mechanism (see {@code @EnableMethodSecurity} in {@code SecurityConfig}).
     * NOTE: an anonymous caller hitting an {@code .authenticated()} route is
     * a DIFFERENT exception, at a different layer entirely - see {@code
     * RestAuthenticationEntryPoint}'s javadoc for why that case can't be
     * handled here.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiEnvelope.failure(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.getDefaultMessage(), null));
    }

    /**
     * Covers {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * too - it's a subtype, and Spring dispatches to the closest matching
     * handler in the exception's hierarchy, so a single handler on the
     * parent type is sufficient rather than needing one for each. A
     * concurrent-edit conflict is retryable by the client, hence 409 rather
     * than 500 - contrast with the generic fallback handler below, which is
     * for faults the client can't do anything about.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());

        return ResponseEntity
                .status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.CONFLICT.name(),
                        "This resource was modified by another request. Please refresh and try again.",
                        null
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database constraint violation triggered: {}", ex.getMessage());

        // Obfuscate the actual SQL error from the client for security
        return ResponseEntity
                .status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.CONFLICT.name(),
                        "The operation could not be completed because it conflicts with existing data.",
                        null
                ));
    }

    /**
     * Thrown by application code to signal invalid runtime/object state
     * rather than a client-facing business rule (which should use
     * {@link AppException} instead). Treated as a server-side fault - a 500,
     * not a 400 - and logged with the full stack trace, because reaching
     * this state is itself a bug: correct code shouldn't let its objects
     * get here regardless of what the client sent. The exception's own
     * message is never returned to the caller since it's written for a
     * developer reading logs, not an API consumer, and may reference
     * internal fields or invariants.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state reached: ", ex);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                        null
                ));
    }

    /**
     * Unlike {@link IllegalStateException} above, this one is usually thrown
     * because of what the caller sent (an out-of-range value, an unexpected
     * enum, a malformed argument some lower layer didn't validate via Bean
     * Validation) - so it's treated as a 400, not a 500. Still never echoes
     * {@code ex.getMessage()} back to the caller: unlike {@link AppException},
     * this exception type is thrown all over the JDK and third-party
     * libraries for reasons that were never designed to be a public API
     * contract, and could easily contain an internal value, field name, or
     * class name that shouldn't be exposed.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.BAD_REQUEST.name(),
                        ErrorCode.BAD_REQUEST.getDefaultMessage(),
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleFallbackException(Exception ex) {
        log.error("Unhandled system fault: ", ex);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiEnvelope.failure(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                        null
                ));
    }
}