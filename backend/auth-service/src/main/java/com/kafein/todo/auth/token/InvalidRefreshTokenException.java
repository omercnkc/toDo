package com.kafein.todo.auth.token;

import com.kafein.todo.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Refresh token yok, bilinmiyor, süresi dolmuş, iptal edilmiş veya kullanıcı pasif.
 * Hepsi istemciye aynı görünür (401 {@code INVALID_REFRESH_TOKEN}); ayrıntı yalnızca loglanır.
 * Controller bu hatada cookie'yi de siler.
 */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Oturumun süresi doldu, lütfen tekrar giriş yapın");
    }
}
