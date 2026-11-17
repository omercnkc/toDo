package com.kafein.todo.dashboard.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * project-service'e tek giriş noktası. Ulaşılamıyor / 5xx / zaman aşımı / beklenmeyen 4xx →
 * 503 {@code PROJECT_SERVICE_UNAVAILABLE} (dashboard kısmen değil, açıkça hata verir; karar 6-C'de gözden geçirilir).
 */
@Service
public class ProjectDirectory {

    private static final Logger log = LoggerFactory.getLogger(ProjectDirectory.class);

    private final ProjectClient projectClient;

    public ProjectDirectory(ProjectClient projectClient) {
        this.projectClient = projectClient;
    }

    public List<ProjectMembership> memberships(long userId) {
        try {
            return projectClient.memberships(userId);
        } catch (FeignException e) {
            log.warn("project-service çağrısı başarısız (HTTP {}): {}", e.status(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROJECT_SERVICE_UNAVAILABLE",
                    "Proje bilgilerine şu an ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.");
        }
    }
}
