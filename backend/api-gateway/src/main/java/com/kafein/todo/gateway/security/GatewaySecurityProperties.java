package com.kafein.todo.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code gateway.security.*} ayarları.
 *
 * @param openPaths       JWT gerektirmeyen yol kalıpları (PathPattern sözdizimi, /api önekiyle)
 * @param jwtSecret       auth-service ile aynı HS256 anahtarı ({@code JWT_SECRET})
 * @param allowedOrigins  tarayıcı isteklerinin gelebileceği origin'ler ({@code FRONTEND_ORIGIN})
 * @param cookieAuthPaths cookie ile kimlik doğrulayan yollar; buralarda {@code Origin} kontrol edilir (CSRF savunması)
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(List<String> openPaths,
                                        String jwtSecret,
                                        List<String> allowedOrigins,
                                        List<String> cookieAuthPaths) {

    public GatewaySecurityProperties {
        openPaths = openPaths == null ? List.of() : List.copyOf(openPaths);
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        cookieAuthPaths = cookieAuthPaths == null ? List.of() : List.copyOf(cookieAuthPaths);
    }
}
