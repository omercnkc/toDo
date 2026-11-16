package com.kafein.todo.task.config;

import com.kafein.todo.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Ortak {@code GlobalExceptionHandler}'dan önce çalışır (o, bilinmeyen hataları 500'e çevirir).
 * Görev aynı anda başka bir istekle değiştirildiyse ({@code @Version} uyuşmazlığı) 409 döner;
 * istemci görevi yeniden yükleyip tekrar dener.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.CONFLICT.value(), "TASK_CONFLICT",
                "Görev bu sırada başka biri tarafından değiştirildi. Lütfen sayfayı yenileyip tekrar deneyin.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
