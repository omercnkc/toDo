package com.kafein.todo.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.security.web.ApiErrorAccessDeniedHandler;
import com.kafein.todo.common.security.web.ApiErrorAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * common-lib'i kullanan <b>servlet</b> servislerine ortak hata yönetimini otomatik ekler.
 * Gateway (WebFlux) için koşul sağlanmaz, hiçbir şey yüklenmez.
 * <p>
 * Kayıt: {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.web.AuthenticationEntryPoint")
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Servisler SecurityFilterChain'de {@code exceptionHandling(...)} ile kullanır. */
    @Bean
    @ConditionalOnMissingBean
    public ApiErrorAuthenticationEntryPoint apiErrorAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiErrorAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiErrorAccessDeniedHandler apiErrorAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiErrorAccessDeniedHandler(objectMapper);
    }
}
