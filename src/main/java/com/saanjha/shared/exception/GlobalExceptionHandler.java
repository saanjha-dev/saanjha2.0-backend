package com.saanjha.shared.exception;

import com.saanjha.shared.api.ApiEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
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