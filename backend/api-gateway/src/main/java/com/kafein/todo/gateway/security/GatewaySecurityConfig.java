package com.kafein.todo.gateway.security;

import com.kafein.todo.common.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class GatewaySecurityConfig {

    /** Gateway token üretmez, yalnızca doğrular. Secret boş/kısaysa gateway açılmaz. */
    @Bean
    JwtService jwtService(GatewaySecurityProperties properties) {
        return JwtService.verifierOnly(properties.jwtSecret(), Clock.systemUTC());
    }
}
