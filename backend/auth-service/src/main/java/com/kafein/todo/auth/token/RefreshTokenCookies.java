package com.kafein.todo.auth.token;

import com.kafein.todo.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code refresh_token} cookie'si (docs/07): HttpOnly; Secure (COOKIE_SECURE); SameSite=Strict;
 * Path=/api/auth; Max-Age=7 gün.
 * <p>
 * Path, tarayıcının gördüğü yoldur (gateway'deki /api önekiyle). Böylece cookie yalnızca
 * /api/auth/refresh ve /api/auth/logout isteklerine gider, diğer API çağrılarına eklenmez.
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private final Duration ttl;
    private final boolean secure;

    public RefreshTokenCookies(AuthProperties properties) {
        this.ttl = properties.refreshToken().ttl();
        this.secure = properties.refreshToken().cookieSecure();
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(ttl).build();
    }

    /** Tarayıcıya cookie'yi silmesini söyler (aynı ad + Path, {@code Max-Age=0}). */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
