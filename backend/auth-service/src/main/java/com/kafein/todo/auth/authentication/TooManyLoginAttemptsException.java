package com.kafein.todo.auth.authentication;

import com.kafein.todo.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Duration;

/** 429: e-posta hatalı giriş sınırına takıldı ({@link LoginThrottle}). Yanıtta {@code Retry-After} başlığı olur. */
public class TooManyLoginAttemptsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                "Çok fazla hatalı giriş denemesi. " + minutes(retryAfter) + " dakika sonra tekrar deneyin.");
        this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** Yukarı yuvarlanır: 30 sn kaldıysa "1 dakika". */
    private static long minutes(Duration retryAfter) {
        return Math.max(1, (retryAfter.toSeconds() + 59) / 60);
    }
}
