package com.kafein.todo.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * project-service uçları. Adres Eureka'dan ({@code lb://project-service}); kullanıcının token'ı iletilir.
 * Doğrudan kullanılmaz: hata dönüşümü {@link ProjectDirectory}'de.
 */
@FeignClient(name = "project-service")
public interface ProjectClient {

    /** Kullanıcının tüm üyelikleri (proje adı, rol, proje durumu), proje id'sine göre; üyeliği yoksa boş. */
    @GetMapping("/internal/projects/memberships")
    List<ProjectMembership> memberships(@RequestParam("userId") long userId);

    /** Sistemdeki tüm projeler (id, ad, durum); yalnızca ADMIN token'ıyla (değilse 403). */
    @GetMapping("/internal/projects/all")
    List<ProjectSummary> all();
}
