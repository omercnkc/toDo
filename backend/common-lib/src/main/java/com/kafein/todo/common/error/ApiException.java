package com.kafein.todo.common.error;

import org.springframework.http.HttpStatus;

/**
 * İş kuralı ihlallerinde servis katmanından fırlatılan hata. {@code GlobalExceptionHandler}
 * bunu {@link ApiError} gövdesine çevirir; {@code message} istemciye gösterilir.
 *
 * @see com.kafein.todo.common.web.GlobalExceptionHandler
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static ApiException badRequest(String error, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, error, message);
    }

    public static ApiException unauthorized(String error, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, error, message);
    }

    public static ApiException forbidden(String error, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, error, message);
    }

    public static ApiException notFound(String error, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, error, message);
    }

    public static ApiException conflict(String error, String message) {
        return new ApiException(HttpStatus.CONFLICT, error, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Makine tarafından okunacak hata kodu (örn. {@code EMAIL_ALREADY_EXISTS}). */
    public String getError() {
        return error;
    }
}
