package com.kafein.todo.task.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectDirectoryTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET,
            "http://project-service/internal/projects/1/members/2", Map.of(), null, StandardCharsets.UTF_8, null);

    private final ProjectClient projectClient = mock(ProjectClient.class);
    private final ProjectDirectory directory = new ProjectDirectory(projectClient);

    @Test
    void uyeyseUyelikDonerDegilseBos() {
        ProjectMembership owner = new ProjectMembership(1L, 2L, ProjectRole.OWNER, ProjectStatus.ARCHIVED);
        when(projectClient.membership(1L, 2L)).thenReturn(owner);
        when(projectClient.membership(1L, 3L)).thenThrow(new FeignException.NotFound("yok", REQUEST, null, Map.of()));

        assertThat(directory.membership(1L, 2L)).contains(owner);
        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.isProjectArchived()).isTrue();
        assertThat(directory.membership(1L, 3L)).isEmpty();
    }

    @Test
    void projeYoksaVeyaErisimYoksaBos() {
        ProjectSummary project = new ProjectSummary(1L, "Web", ProjectStatus.ACTIVE);
        when(projectClient.project(1L)).thenReturn(project);
        when(projectClient.project(2L)).thenThrow(new FeignException.NotFound("yok", REQUEST, null, Map.of()));
        when(projectClient.project(3L)).thenThrow(new FeignException.Forbidden("yasak", REQUEST, null, Map.of()));

        assertThat(directory.project(1L)).contains(project);
        assertThat(project.isArchived()).isFalse();
        assertThat(directory.project(2L)).isEmpty();
        assertThat(directory.project(3L)).isEmpty();
    }

    @Test
    void ulasilamayanProjeServisi503Olur() {
        when(projectClient.membership(1L, 2L)).thenThrow(new RetryableException(-1, "Connection refused",
                Request.HttpMethod.GET, (Long) null, REQUEST));
        when(projectClient.project(1L)).thenThrow(
                new FeignException.InternalServerError("hata", REQUEST, null, Map.of()));

        assertThatThrownBy(() -> directory.membership(1L, 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(503);
                    assertThat(e.getError()).isEqualTo("PROJECT_SERVICE_UNAVAILABLE");
                });
        assertThatThrownBy(() -> directory.project(1L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void yetkiHatasiDisindakiIstemciHatalariDa503Olur() {
        // 401: token iletilmemiş/geçersiz → yapılandırma hatası, kullanıcıya "erişim yok" denmemeli
        when(projectClient.membership(1L, 2L)).thenThrow(
                new FeignException.Unauthorized("token yok", REQUEST, null, Map.of()));

        assertThatThrownBy(() -> directory.membership(1L, 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
    }
}
