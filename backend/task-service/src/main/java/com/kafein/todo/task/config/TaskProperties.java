package com.kafein.todo.task.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * {@code task.*} ayarları (application.yml). Geçersiz değerde servis açılmaz.
 *
 * @param timeZone "bugün"ün hangi saat dilimine göre hesaplanacağı (gecikmiş görev filtresi). Sunucu UTC'de
 *                 çalışsa da kullanıcılar Türkiye saatinde; gece 00:00–03:00 arasında yanlış gün kullanılmasın.
 */
@Validated
@ConfigurationProperties(prefix = "task")
public record TaskProperties(@Valid @NotNull Jwt jwt,
                             @DefaultValue("Europe/Istanbul") @NotNull ZoneId timeZone) {

    /** @param secret auth-service ile aynı HS256 anahtarı; uzunluk kontrolünü {@code JwtService} yapar */
    public record Jwt(String secret) {

        @Override
        public String toString() {
            return "Jwt[secret=***]";
        }
    }
}
