package com.kafein.todo.project.client;

import com.kafein.todo.common.security.JwtService;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * task-service'in dahili uçları (docs/04 "TaskClient"). Yalnızca outbox relay'i kullanır: kullanıcı isteği
 * dışında çalıştığı için iletilecek kullanıcı token'ı yoktur, her çağrıya kısa ömürlü servis token'ı eklenir
 * ({@link ServiceTokenConfig}). Uçlar idempotenttir; aynı olay tekrar gönderilebilir.
 */
@FeignClient(name = "task-service", path = "/internal/tasks/by-project",
        configuration = TaskClient.ServiceTokenConfig.class)
public interface TaskClient {

    @DeleteMapping("/{projectId}")
    void deleteProjectTasks(@PathVariable("projectId") long projectId);

    @DeleteMapping("/{projectId}/assignees/{userId}")
    void unassignMember(@PathVariable("projectId") long projectId, @PathVariable("userId") long userId);

    /**
     * Yalnızca bu client'a uygulanır. {@code @Configuration} değildir: olsaydı component taramasıyla tüm Feign
     * client'larına (AuthClient dahil) yayılırdı.
     */
    class ServiceTokenConfig {

        @Bean
        public RequestInterceptor serviceTokenInterceptor(JwtService jwtService,
                                                          @Value("${spring.application.name}") String serviceName) {
            return template -> {
                // Olası iletilmiş kullanıcı token'ı yerine her zaman servis token'ı (her çağrıda yeni, 1 dk)
                template.removeHeader(HttpHeaders.AUTHORIZATION);
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateServiceToken(serviceName));
            };
        }
    }
}
