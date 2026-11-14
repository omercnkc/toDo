package com.kafein.todo.project.config;

import com.kafein.todo.common.security.JwtAuthenticationFilter;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.web.ApiErrorAccessDeniedHandler;
import com.kafein.todo.common.security.web.ApiErrorAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * Tüm uçlar giriş gerektirir; kimlik Bearer JWT'den gelir (auth-service üretir, bu servis yalnızca doğrular).
 * Proje bazlı yetki (üye / OWNER / ADMIN) servis katmanında kontrol edilir (docs/07), burada değil.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(ProjectProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            ApiErrorAuthenticationEntryPoint authenticationEntryPoint,
                                            ApiErrorAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // Yalnızca Bearer header'lı API, cookie yok → CSRF riski yok
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Yalnızca doğrulama: bu servisin token üretmesine gerek yok. */
    @Bean
    JwtService jwtService(ProjectProperties properties, Clock clock) {
        return JwtService.verifierOnly(properties.jwt().secret(), clock);
    }

    /** Zaman tek kaynaktan (davet yanıt zamanı vb.): testlerde sabit saat verilebilir. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
