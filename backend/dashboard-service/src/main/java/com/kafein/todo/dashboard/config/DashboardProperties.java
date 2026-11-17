package com.kafein.todo.dashboard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code dashboard.*} ayarları (application.yml). Geçersiz değerde servis açılmaz.
 *
 * @param cacheTtl özetin önbellekte kalma süresi (kullanıcı + kapsam başına). Görev değişikliği en geç bu sürede
 *                 yansır; {@code 0} önbelleği kapatır.
 */
@Validated
@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(@Valid @NotNull Jwt jwt, @DefaultValue("30s") @NotNull Duration cacheTtl) {

    /** @param secret auth-service ile aynı HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret) {

        @Override
        public String toString() {
            return "Jwt[secret=***]";
        }
    }
}
