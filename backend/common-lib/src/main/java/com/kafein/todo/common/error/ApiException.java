package com.kafein.todo.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * İş kuralı ihlallerinde servis katmanından fırlatılan hata. {@code GlobalExceptionHandler}
 * bunu {@link ApiError} gövdesine çevirir; {@code message} istemciye gösterilir.
 * <p>
 * Hata belirli bir form alanına aitse ({@link #withField}) {@code fieldErrors} de doldurulur;
 * frontend mesajı o alanın altında gösterebilir (docs/08).
 *
 * @see com.kafein.todo.common.web.GlobalExceptionHandler
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String error, String message) {
        this(status, error, message, List.of());
    }

    public ApiException(HttpStatus status, String error, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.error = error;
        this.fieldErrors = List.copyOf(fieldErrors);
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

    /** Aynı hata, bir form alanına bağlanmış olarak (örn. {@code email} → "zaten kayıtlı"). */
    public ApiException withField(String field, String fieldMessage) {
        return new ApiException(status, error, getMessage(), List.of(new ApiError.FieldError(field, fieldMessage)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Makine tarafından okunacak hata kodu (örn. {@code EMAIL_ALREADY_EXISTS}). */
    public String getError() {
        return error;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
