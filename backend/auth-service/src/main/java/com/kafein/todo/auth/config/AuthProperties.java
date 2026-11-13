package com.kafein.todo.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code auth.*} ayarları (application.yml). Geçersiz değerde servis açılmaz. */
@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@Valid @NotNull Jwt jwt,
                             @Valid @NotNull RefreshToken refreshToken,
                             @Valid @NotNull Password password) {

    /** @param secret HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret, @NotNull Duration accessTokenTtl) {
    }

    public record RefreshToken(@NotNull Duration ttl, boolean cookieSecure) {
    }

    public record Password(@Min(4) @Max(31) int bcryptStrength) {
    }
}
