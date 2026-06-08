package com.saanjha.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiEnvelope<T> {
    private final boolean success;
    private final T data;
    private final ErrorDetails error;
    private final Map<String, Object> meta;
    private final Instant timestamp;

    private ApiEnvelope(boolean success, T data, ErrorDetails error, Map<String, Object> meta) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.meta = meta != null ? meta : Collections.emptyMap();
        this.timestamp = Instant.now();
    }

    public static <T> ApiEnvelope<T> success(T data) {
        return new ApiEnvelope<>(true, data, null, null);
    }

    public static <T> ApiEnvelope<T> success(T data, Map<String, Object> meta) {
        return new ApiEnvelope<>(true, data, null, meta);
    }

    public static <T> ApiEnvelope<T> failure(String code, String message, Map<String, Object> details) {
        return new ApiEnvelope<>(false, null, new ErrorDetails(code, message, details), null);
    }

    @Getter
    public static class ErrorDetails {
        private final String code;
        private final String message;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private final Map<String, Object> details;

        public ErrorDetails(String code, String message, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.details = details != null ? details : Collections.emptyMap();
        }
    }
}