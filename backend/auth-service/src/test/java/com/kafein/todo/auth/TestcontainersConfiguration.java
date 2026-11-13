package com.kafein.todo.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testler için gerçek MySQL 8 (compose ile aynı imaj). {@code @ServiceConnection} datasource
 * ayarlarını konteynere yönlendirir; Flyway migration'ları da bu veritabanında çalışır.
 * Docker çalışıyor olmalı.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    }
}
