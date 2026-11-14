package com.kafein.todo.project.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code project.*} ayarları (application.yml). Geçersiz değerde servis açılmaz. */
@Validated
@ConfigurationProperties(prefix = "project")
public record ProjectProperties(@Valid @NotNull Jwt jwt) {

    /** @param secret auth-service ile aynı HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret) {

        @Override
        public String toString() {
            return "Jwt[secret=***]";
        }
    }
}
