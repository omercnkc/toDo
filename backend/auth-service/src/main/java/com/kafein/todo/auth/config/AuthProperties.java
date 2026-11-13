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
                             @Valid @NotNull Password password,
                             @Valid @NotNull Admin admin) {

    /** @param secret HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret, @NotNull Duration accessTokenTtl) {

        @Override
        public String toString() {
            return "Jwt[secret=***, accessTokenTtl=" + accessTokenTtl + "]";
        }
    }

    /**
     * @param reuseGrace rotasyonla iptal edilmiş token bu süre içinde tekrar gelirse eşzamanlı
     *                   yenileme sayılır (alarm yok); sonrasında tekrar kullanım = tüm oturumlar iptal
     */
    public record RefreshToken(@NotNull Duration ttl, boolean cookieSecure, @NotNull Duration reuseGrace) {
    }

    public record Password(@Min(4) @Max(31) int bcryptStrength) {
    }

    /**
     * İlk açılışta oluşturulan ADMIN ({@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}).
     * İkisinden biri boşsa seed atlanır.
     */
    public record Admin(String email, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "Admin[email=" + email + ", password=***]";
        }
    }
}
