package com.kafein.todo.auth.config;

import com.kafein.todo.common.security.JwtAuthenticationFilter;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.web.ApiErrorAccessDeniedHandler;
import com.kafein.todo.common.security.web.ApiErrorAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableMethodSecurity // @PreAuthorize (ADMIN uçları, adım 2-D)
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /**
     * Yollar gateway'in StripPrefix'inden sonraki hâlleriyle yazılır (/api/auth/login → /auth/login).
     * refresh ve logout access token değil refresh cookie'si ile çalışır (adım 2-C).
     */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            ApiErrorAuthenticationEntryPoint authenticationEntryPoint,
                                            ApiErrorAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // Bearer header'lı API: tarayıcı header'ı kendiliğinden eklemez, CSRF riski yok.
                // Cookie kullanan refresh/logout SameSite=Strict + gateway Origin kontrolüyle korunur (docs/07).
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable) // Spring'in /logout ucu değil, kendi /auth/logout ucumuz
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtService jwtService(AuthProperties properties, Clock clock) {
        return new JwtService(properties.jwt().secret(), properties.jwt().accessTokenTtl(), clock);
    }

    @Bean
    PasswordEncoder passwordEncoder(AuthProperties properties) {
        return new BCryptPasswordEncoder(properties.password().bcryptStrength());
    }

    /** Zaman tek kaynaktan: testlerde sabit saat verilebilir. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
