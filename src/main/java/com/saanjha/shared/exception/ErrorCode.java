package com.saanjha.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Payload failed validation constraints."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Missing, invalid, or expired authentication token."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Insufficient permissions to access this resource."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource could not be found."),
    CONFLICT(HttpStatus.CONFLICT, "The request could not be completed due to a conflict with the current state of the target resource."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}