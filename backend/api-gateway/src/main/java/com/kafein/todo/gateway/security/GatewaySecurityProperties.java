package com.kafein.todo.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code gateway.security.*} ayarları.
 *
 * @param openPaths JWT gerektirmeyen yol kalıpları (PathPattern sözdizimi, /api önekiyle)
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(List<String> openPaths) {

    public GatewaySecurityProperties {
        openPaths = openPaths == null ? List.of() : List.copyOf(openPaths);
    }
}
