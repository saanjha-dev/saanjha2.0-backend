package com.saanjha.shared.exception;

import com.saanjha.shared.api.ApiEnvelope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirmed via repository audit that these exception types reached only the
 * generic 500 fallback before the hardening sprint (grep showed zero
 * existing @ExceptionHandler coverage for any of them, and zero per-
 * controller local handling - see GlobalExceptionHandler class javadoc
 * history / hardening-sprint commit). Each test below asserts both the
 * correct HTTP status/error code AND, for exception types not designed as a
 * public API contract (IllegalArgumentException, IllegalStateException,
 * HttpMessageNotReadableException), that the raw exception message is never
 * echoed back to the caller.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void appException_mapsToItsOwnErrorCodeAndMessage() {
        AppException ex = new AppException(ErrorCode.NOT_FOUND, "Project not found.");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Project not found.");
    }

    @Test
    void methodArgumentNotValid_returns422WithFieldErrors() throws NoSuchMethodException {
        MethodArgumentNotValidException ex = methodArgumentNotValidException();

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().getError().getDetails()).containsEntry("email", "must not be blank");
    }

    @Test
    void bindException_returns422WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "filter");
        bindingResult.addError(new FieldError("filter", "page", "must be positive"));
        BindException ex = new BindException(bindingResult);

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleBindException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getError().getDetails()).containsEntry("page", "must be positive");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void constraintViolation_returns422WithPropertyPaths() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<?>> violations = (Set) validator.validate(new SampleParams(-1));
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException("invalid", violations);

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getError().getDetails()).containsKey("count");
    }

    @Test
    void httpMessageNotReadable_returns400AndNeverLeaksParserMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character ('}' (code 125)) at [Source: (String)\"{invalid\"; line: 1, column: 9]",
                (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getMessage()).doesNotContain("JSON parse error");
    }

    @Test
    void accessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("not allowed");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getError().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void optimisticLockingFailure_returns409() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("stale row version");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleOptimisticLockingFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError().getCode()).isEqualTo("CONFLICT");
    }

    @Test
    void dataIntegrityViolation_returns409AndObfuscatesSqlDetail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"auth_users_email_key\"");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError().getMessage()).doesNotContain("auth_users_email_key");
    }

    @Test
    void illegalState_returns500AndNeverLeaksMessage() {
        IllegalStateException ex = new IllegalStateException("session already finalized, internal invariant violated");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getMessage()).doesNotContain("internal invariant");
    }

    @Test
    void illegalArgument_returns400AndNeverLeaksMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("field 'internalRankingWeight' must be positive");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getMessage()).doesNotContain("internalRankingWeight");
    }

    @Test
    void unhandledException_fallsBackTo500AndNeverLeaksMessage() {
        RuntimeException ex = new RuntimeException("NullPointerException at com.saanjha.internal.SecretClass:42");

        ResponseEntity<ApiEnvelope<Void>> response = handler.handleFallbackException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getMessage()).doesNotContain("SecretClass");
    }

    private MethodArgumentNotValidException methodArgumentNotValidException() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        Method method = SampleParams.class.getMethod("value");
        MethodParameter parameter = new MethodParameter(method, -1);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    /** Minimal target for a real Jakarta Bean Validation pass in the ConstraintViolationException test. */
    private record SampleParams(@jakarta.validation.constraints.Min(0) int count) {
        public int value() {
            return count;
        }
    }
}
