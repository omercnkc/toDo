package com.kafein.todo.dashboard.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * task-service'e tek giriş noktası.
 * <ul>
 *   <li>403 (USER {@code scope=all} istedi) → 403 {@code STATS_SCOPE_FORBIDDEN}: yetki kararı task-service'in,
 *       olduğu gibi iletilir (dashboard yine de rolü önceden kontrol eder).</li>
 *   <li>Ulaşılamıyor / 5xx / zaman aşımı / diğer hatalar → 503 {@code TASK_SERVICE_UNAVAILABLE}.</li>
 * </ul>
 */
@Service
public class TaskDirectory {

    private static final Logger log = LoggerFactory.getLogger(TaskDirectory.class);

    private final TaskClient taskClient;

    public TaskDirectory(TaskClient taskClient) {
        this.taskClient = taskClient;
    }

    public TaskStats stats(String scope) {
        try {
            return taskClient.stats(scope);
        } catch (FeignException.Forbidden e) {
            throw ApiException.forbidden("STATS_SCOPE_FORBIDDEN",
                    "Tüm sistemin istatistiklerini yalnızca yöneticiler görebilir");
        } catch (FeignException e) {
            log.warn("task-service çağrısı başarısız (HTTP {}): {}", e.status(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TASK_SERVICE_UNAVAILABLE",
                    "Görev istatistiklerine şu an ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.");
        }
    }
}
