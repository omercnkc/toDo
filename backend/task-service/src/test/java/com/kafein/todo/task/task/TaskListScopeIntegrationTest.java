package com.kafein.todo.task.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.IntegrationTestSupport;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /tasks} {@code projectId} olmadan: kullanıcının üye olduğu tüm projeler (Faz 5-B). */
class TaskListScopeIntegrationTest extends IntegrationTestSupport {

    private Task seed(long projectId, String title, TaskPriority priority, AuthenticatedUser reporter) {
        return tasks.saveAndFlush(new Task(projectId, title, null, priority, null, reporter.id(), null, 0));
    }

    /** başlık → canDelete */
    private Map<String, Boolean> myTasks(AuthenticatedUser as, String... params) throws Exception {
        var request = get("/tasks").param("size", "100");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        JsonNode body = json(perform(request, as).andExpect(status().isOk()));
        Map<String, Boolean> result = new HashMap<>();
        body.get("content").forEach(t -> result.put(t.get("title").asText(), t.get("canDelete").asBoolean()));
        assertThat(body.get("totalElements").asLong()).isEqualTo(result.size());
        return result;
    }

    @Test
    void projeVerilmezseUyeOlunanTumProjelerSilmeYetkisiProjeBazinda() throws Exception {
        long first = newProject();
        long second = newProject();
        archive(second);
        seed(first, "Birinci - sahibin", TaskPriority.HIGH, owner);
        seed(first, "Birinci - üyenin", TaskPriority.LOW, member);
        seed(second, "Arşivdeki", TaskPriority.HIGH, member2);

        // owner'ın üye olmadığı bir proje: owner'ın listesine girmez
        long foreign = newProject(member2, member);
        seed(foreign, "Yabancı proje", TaskPriority.HIGH, member2);

        assertThat(myTasks(owner)).containsOnly(
                Map.entry("Birinci - sahibin", true),
                Map.entry("Birinci - üyenin", true),
                Map.entry("Arşivdeki", true));
        // member: kendi oluşturduğunu silebilir, başkasınınkini (OWNER değil) silemez; üç projenin de üyesi
        assertThat(myTasks(member)).containsOnly(
                Map.entry("Birinci - sahibin", false),
                Map.entry("Birinci - üyenin", true),
                Map.entry("Arşivdeki", false),
                Map.entry("Yabancı proje", false));
        assertThat(myTasks(member, "priority", "HIGH", "sort", "title"))
                .containsOnlyKeys("Arşivdeki", "Birinci - sahibin", "Yabancı proje");
    }

    @Test
    void uyeligiOlmayanBosListeAlirUyeOlmayanAdminDe() throws Exception {
        long projectId = newProject();
        seed(projectId, "Gizli", TaskPriority.MEDIUM, owner);

        perform(get("/tasks"), stranger).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
        // ADMIN tüm sistemi tek listede görmez; tek projeye projectId ile erişir
        assertThat(myTasks(admin)).isEmpty();
        perform(get("/tasks").param("projectId", String.valueOf(projectId)), admin)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void kapsamliListedeFiltreDogrulamasiVeProjeServisiKesintisi() throws Exception {
        newProject();
        perform(get("/tasks").param("status", "YOK"), member).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        Request request = Request.create(Request.HttpMethod.GET, "http://project-service", Map.of(), null,
                StandardCharsets.UTF_8, null);
        doThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request))
                .when(projectClient).memberships(anyLong());
        perform(get("/tasks"), member).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROJECT_SERVICE_UNAVAILABLE"));
    }
}
