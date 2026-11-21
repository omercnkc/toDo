package com.kafein.todo.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * {@code gateway.rate-limit.*}: kimlik uçlarına IP başına istek sınırı ({@link AuthRateLimitFilter}).
 *
 * @param paths       sınırlanan yollar (yalnızca POST), gateway'e gelen hâliyle ({@code /api/auth/login})
 * @param maxRequests bir pencerede IP + yol başına izin verilen istek
 * @param window      sabit pencere süresi
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record AuthRateLimitProperties(List<String> paths, int maxRequests, Duration window) {

    public AuthRateLimitProperties {
        paths = paths == null ? List.of() : List.copyOf(paths);
        if (maxRequests < 1) {
            maxRequests = 20;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofMinutes(1);
        }
    }
}
