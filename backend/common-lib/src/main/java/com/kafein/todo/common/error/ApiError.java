package com.kafein.todo.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Tüm servislerin döndüğü ortak hata gövdesi (bkz. docs/06-api-contracts.md).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError withFieldErrors(int status, String error, String message, String path,
                                           List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, List.copyOf(fieldErrors));
    }

    public record FieldError(String field, String message) {
    }
}
