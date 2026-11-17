package com.kafein.todo.dashboard.config;

import com.kafein.todo.common.security.JwtAuthenticationFilter;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.common.security.web.ApiErrorAccessDeniedHandler;
import com.kafein.todo.common.security.web.ApiErrorAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * Tüm uçlar kullanıcı token'ı (USER/ADMIN) ister; servis token'ı kabul edilmez (dahili ucu yok). Bu servis
 * token üretmez, yalnızca doğrular; aynı token Feign ile project-service ve task-service'e iletilir.
 */
@Configuration
@EnableConfigurationProperties(DashboardProperties.class)
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
                        .anyRequest().hasAnyRole(Role.USER.name(), Role.ADMIN.name()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtService jwtService(DashboardProperties properties, Clock clock) {
        return JwtService.verifierOnly(properties.jwt().secret(), clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
