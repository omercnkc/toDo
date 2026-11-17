package com.kafein.todo.task.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * project-service'teki proje/üyelik bilgisine tek giriş noktası. Feign hatalarını anlamlı sonuçlara çevirir:
 * <ul>
 *   <li>üye değil / proje yok (404) ya da erişim yok (403) → {@link Optional#empty()}</li>
 *   <li>project-service'e ulaşılamıyor / 5xx / zaman aşımı → 503 {@code PROJECT_SERVICE_UNAVAILABLE}</li>
 * </ul>
 * Yetki kararı (404 mü 403 mü, arşivde yazma 409 mu) çağıran servisin işidir.
 */
@Service
public class ProjectDirectory {

    private static final Logger log = LoggerFactory.getLogger(ProjectDirectory.class);

    private final ProjectClient projectClient;

    public ProjectDirectory(ProjectClient projectClient) {
        this.projectClient = projectClient;
    }

    /** Kullanıcının projedeki üyeliği; üye değilse veya proje yoksa boş. */
    public Optional<ProjectMembership> membership(long projectId, long userId) {
        try {
            return Optional.of(projectClient.membership(projectId, userId));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw unavailable(e);
        }
    }

    /** İsteği yapan kullanıcının görebildiği proje; yoksa veya erişemiyorsa boş. */
    public Optional<ProjectSummary> project(long projectId) {
        try {
            return Optional.of(projectClient.project(projectId));
        } catch (FeignException.NotFound | FeignException.Forbidden e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw unavailable(e);
        }
    }

    /** Kullanıcının üye olduğu tüm projeler (arşivdekiler dahil). */
    public List<ProjectMembership> memberships(long userId) {
        try {
            return projectClient.memberships(userId);
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
