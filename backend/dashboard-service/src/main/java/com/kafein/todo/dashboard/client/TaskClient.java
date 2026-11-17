package com.kafein.todo.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * task-service uçları. Adres Eureka'dan ({@code lb://task-service}); kullanıcının token'ı iletilir (kapsamı
 * task-service kendi yetki kuralıyla belirler). Doğrudan kullanılmaz: hata dönüşümü {@link TaskDirectory}'de.
 */
@FeignClient(name = "task-service")
public interface TaskClient {

    /** {@code scope}: {@code mine} veya {@code all} (yalnızca ADMIN; değilse 403). */
    @GetMapping("/tasks/stats")
    TaskStats stats(@RequestParam("scope") String scope);
}
