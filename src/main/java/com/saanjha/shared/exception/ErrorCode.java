package com.saanjha.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ========================================================================
    // 400: BAD REQUEST & VALIDATION
    // ========================================================================
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is malformed or invalid."),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Payload failed validation constraints."),
    INVALID_INPUT_FORMAT(HttpStatus.BAD_REQUEST, "The provided input format is not supported."),

    // ========================================================================
    // 401: AUTHENTICATION (IDENTITY)
    // ========================================================================
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Missing, invalid, or expired authentication token."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The email or password provided is incorrect."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The authentication token has expired. Please refresh."),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "The authentication token has been revoked or invalidated."),
    ACCOUNT_UNVERIFIED(HttpStatus.UNAUTHORIZED, "The account email has not been verified yet."),

    // ========================================================================
    // 403: AUTHORIZATION (PERMISSIONS & STATE)
    // ========================================================================
    FORBIDDEN(HttpStatus.FORBIDDEN, "Insufficient permissions to access this resource."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "The account has been locked due to suspicious activity."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "The account has been suspended by an administrator."),

    // ========================================================================
    // 404: RESOURCE ROUTING
    // ========================================================================
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource could not be found."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested API endpoint does not exist."),

    // ========================================================================
    // 409: BUSINESS LOGIC CONFLICTS
    // ========================================================================
    CONFLICT(HttpStatus.CONFLICT, "The request could not be completed due to a conflict with the current state."),
    ALREADY_EXISTS(HttpStatus.CONFLICT, "The resource you are trying to create already exists."),
    STATE_TRANSITION_FAILED(HttpStatus.CONFLICT, "The requested action is not valid for the resource's current state."),
    PROJECT_READ_ONLY(HttpStatus.CONFLICT, "This project is completed or archived and can no longer be modified."),

    // ========================================================================
    // 413 & 415: FILE UPLOADS & MEDIA (CLOUDINARY)
    // ========================================================================
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum allowed size."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The provided file format or media type is not supported."),

    // ========================================================================
    // 429: INFRASTRUCTURE LIMITS
    // ========================================================================
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later."),

    // ========================================================================
    // 500: SYSTEM ERRORS
    // ========================================================================
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred."),
    EXTERNAL_SERVICE_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "Communication with an external service (e.g., Cloudinary, Redis) failed."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred while processing the request.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}