package com.kafein.todo.task.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code task.*} ayarları (application.yml). Geçersiz değerde servis açılmaz. */
@Validated
@ConfigurationProperties(prefix = "task")
public record TaskProperties(@Valid @NotNull Jwt jwt) {

    /** @param secret auth-service ile aynı HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret) {

        @Override
        public String toString() {
            return "Jwt[secret=***]";
        }
    }
}
