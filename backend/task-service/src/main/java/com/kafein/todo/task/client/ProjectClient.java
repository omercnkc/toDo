package com.kafein.todo.task.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * project-service uçları (docs/04 "ProjectClient"). Adres Eureka'dan ({@code lb://project-service});
 * kullanıcının token'ı common-lib'deki {@code FeignAuthForwardingInterceptor} ile iletilir.
 * <p>
 * Doğrudan kullanılmaz: hata dönüşümü {@link ProjectDirectory}'de.
 */
@FeignClient(name = "project-service")
public interface ProjectClient {

    /** Üyelik + proje durumu. Üye değilse veya proje yoksa 404 ({@code FeignException.NotFound}). */
    @GetMapping("/internal/projects/{projectId}/members/{userId}")
    ProjectMembership membership(@PathVariable("projectId") long projectId, @PathVariable("userId") long userId);

    /**
     * Proje bilgisi, isteği yapan kullanıcının yetkisiyle: üye olmayan ADMIN'in eriştiği projenin durumunu
     * öğrenmek için. Yoksa 404, erişimi yoksa 403.
     */
    @GetMapping("/projects/{projectId}")
    ProjectSummary project(@PathVariable("projectId") long projectId);

    /** Kullanıcının tüm üyelikleri (rol + proje durumu); üyeliği yoksa boş liste. */
    @GetMapping("/internal/projects/memberships")
    List<ProjectMembership> memberships(@RequestParam("userId") long userId);
}
