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
 * 503 {@code PROJECT_SERVICE_UNAVAILABLE} (dashboard kısmi yanıt vermez, açıkça hata verir; bkz. {@code SummaryService}).
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
            throw unavailable(e);
        }
    }

    /** Tüm projeler (ADMIN). 403 → 403 {@code STATS_SCOPE_FORBIDDEN} (dashboard rolü önceden kontrol eder). */
    public List<ProjectSummary> all() {
        try {
            return projectClient.all();
        } catch (FeignException.Forbidden e) {
            throw ApiException.forbidden("STATS_SCOPE_FORBIDDEN",
                    "Tüm sistemin istatistiklerini yalnızca yöneticiler görebilir");
        } catch (FeignException e) {
            throw unavailable(e);
        }
    }

    private static ApiException unavailable(FeignException e) {
        log.warn("project-service çağrısı başarısız (HTTP {}): {}", e.status(), e.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROJECT_SERVICE_UNAVAILABLE",
                "Proje bilgilerine şu an ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.");
    }
}
