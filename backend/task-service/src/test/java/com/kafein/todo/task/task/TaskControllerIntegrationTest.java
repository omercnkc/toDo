package com.kafein.todo.task.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.task.IntegrationTestSupport;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerIntegrationTest extends IntegrationTestSupport {

    /** Projenin board'u: başlık → "DURUM:pozisyon". */
    private Map<String, String> board(long projectId) {
        Map<String, String> result = new HashMap<>();
        for (Task task : tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId)) {
            result.put(task.getTitle(), task.getStatus() + ":" + task.getPosition());
        }
        return result;
    }

    private static Map<String, Object> updateBody(String title, String priority, Long assigneeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Yeni açıklama");
        body.put("priority", priority);
        body.put("assigneeId", assigneeId);
        body.put("dueDate", "2026-12-01");
        return body;
    }

    // ---------- oluşturma ----------

    @Test
    void uyeGorevOlusturur_TodoKolonununSonunaEklenir() throws Exception {
        long projectId = newProject();

        JsonNode first = json(send(post("/tasks"), member, Map.of(
                "projectId", projectId, "title", "  Giriş ekranı  ", "description", "Ayrıntı",
                "priority", "HIGH", "assigneeId", member2.id(), "dueDate", "2026-11-30"))
                .andExpect(status().isCreated()));
        assertThat(first.get("title").asText()).isEqualTo("Giriş ekranı");
        assertThat(first.get("status").asText()).isEqualTo("TODO");
        assertThat(first.get("priority").asText()).isEqualTo("HIGH");
        assertThat(first.get("position").asInt()).isZero();
        assertThat(first.get("assigneeId").asLong()).isEqualTo(member2.id());
        assertThat(first.get("reporterId").asLong()).isEqualTo(member.id());
        assertThat(first.get("dueDate").asText()).isEqualTo("2026-11-30");
        assertThat(first.get("canDelete").asBoolean()).as("oluşturan silebilir").isTrue();
        assertThat(first.get("createdAt").isNull()).isFalse();

        JsonNode second = json(send(post("/tasks"), owner, Map.of("projectId", projectId, "title", "İkinci"))
                .andExpect(status().isCreated()));
        assertThat(second.get("position").asInt()).isEqualTo(1);
        assertThat(second.get("priority").asText()).as("varsayılan öncelik").isEqualTo("MEDIUM");
        assertThat(second.has("assigneeId") && !second.get("assigneeId").isNull()).isFalse();
    }

    @Test
    void gecersizGovde400VeAlanHatalari() throws Exception {
        long projectId = newProject();

        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("title")));
        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "x".repeat(201),
                "description", "y".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("description")));
        send(post("/tasks"), member, Map.of("title", "Projesiz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("projectId")));
        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "a", "priority", "ACIL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uyeOlmayanaAtanamaz() throws Exception {
        long projectId = newProject();

        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "Görev", "assigneeId", stranger.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ASSIGNEE_NOT_MEMBER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("assigneeId"));
        assertThat(board(projectId)).isEmpty();
    }

    @Test
    void ayniProjedeEsZamanliOlusturmalardaPozisyonlarCakismaz() throws Exception {
        long projectId = newProject();
        int count = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String title = "Paralel " + i;
                calls.add(() -> send(post("/tasks"), member, Map.of("projectId", projectId, "title", title))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId))
                .extracting(Task::getPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, count).boxed().toList());
    }

    // ---------- yetki ----------

    @Test
    void uyeOlmayanErisemez403() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "Gizli", member);

        send(post("/tasks"), stranger, Map.of("projectId", projectId, "title", "Sızma"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        perform(get("/tasks/{id}", taskId), stranger).andExpect(status().isForbidden());
        perform(get("/tasks").param("projectId", String.valueOf(projectId)), stranger)
                .andExpect(status().isForbidden());
        send(put("/tasks/{id}", taskId), stranger, updateBody("Değiştir", "LOW", null))
                .andExpect(status().isForbidden());
        send(patch("/tasks/{id}/status", taskId), stranger, Map.of("status", "DONE"))
                .andExpect(status().isForbidden());
        perform(delete("/tasks/{id}", taskId), stranger).andExpect(status().isForbidden());
        // Olmayan projede de aynı yanıt: proje var mı yok mu sızdırılmaz
        send(post("/tasks"), stranger, Map.of("projectId", 999_999_999L, "title", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uyeOlmayanAdminErisirOlmayanProjede404() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "Görev", member);

        perform(get("/tasks/{id}", taskId), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true));
        createTask(projectId, "Admin görevi", admin);
        send(post("/tasks"), admin, Map.of("projectId", 999_999_999L, "title", "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void olmayanGorev404() throws Exception {
        perform(get("/tasks/{id}", 999_999_999L), member)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TASK_NOT_FOUND"));
        send(put("/tasks/{id}", 999_999_999L), member, updateBody("x", "LOW", null))
                .andExpect(status().isNotFound());
        perform(delete("/tasks/{id}", 999_999_999L), member).andExpect(status().isNotFound());
    }

    @Test
    void arsivlenmisProjedeGorevlerOkunurAmaDegismez409() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "Eski", member);
        archive(projectId);

        perform(get("/tasks/{id}", taskId), member).andExpect(status().isOk());
        perform(get("/tasks").param("projectId", String.valueOf(projectId)), member).andExpect(status().isOk());
        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "Yeni"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));
        send(put("/tasks/{id}", taskId), member, updateBody("Değiştir", "LOW", null))
                .andExpect(status().isConflict());
        send(patch("/tasks/{id}/status", taskId), member, Map.of("status", "DONE"))
                .andExpect(status().isConflict());
        perform(delete("/tasks/{id}", taskId), owner).andExpect(status().isConflict());
    }

    @Test
    void projeServisiKapaliyken503() throws Exception {
        long projectId = newProject();
        Request request = Request.create(Request.HttpMethod.GET, "http://project-service", Map.of(), null,
                StandardCharsets.UTF_8, null);
        // doThrow: when(mock.call()) biçimi mevcut sahte cevabı çalıştırırdı
        doThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request))
                .when(projectClient).membership(anyLong(), anyLong());

        send(post("/tasks"), member, Map.of("projectId", projectId, "title", "x"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROJECT_SERVICE_UNAVAILABLE"));
    }

    // ---------- okuma ----------

    @Test
    void listeEnYenilerOnceVeSayfali() throws Exception {
        long projectId = newProject();
        for (int i = 1; i <= 3; i++) {
            createTask(projectId, "Görev " + i, member);
        }
        createTask(newProject(), "Başka proje", member);

        perform(get("/tasks").param("projectId", String.valueOf(projectId)).param("size", "2"), member2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Görev 3"))
                .andExpect(jsonPath("$.content[1].title").value("Görev 2"))
                .andExpect(jsonPath("$.content[0].canDelete").value(false));
        perform(get("/tasks").param("projectId", String.valueOf(projectId)).param("size", "101"), member)
                .andExpect(status().isBadRequest());
        perform(get("/tasks"), member).andExpect(status().isBadRequest());
    }

    // ---------- düzenleme ----------

    @Test
    void uyeGorevDuzenler() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "Eski başlık", member);

        send(put("/tasks/{id}", taskId), member2, updateBody(" Yeni başlık ", "LOW", owner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Yeni başlık"))
                .andExpect(jsonPath("$.description").value("Yeni açıklama"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.assigneeId").value(owner.id()))
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.reporterId").value(member.id()));

        send(put("/tasks/{id}", taskId), member2, updateBody("Yeni", "LOW", stranger.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ASSIGNEE_NOT_MEMBER"));
        send(put("/tasks/{id}", taskId), member2, updateBody("Yeni", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priority"));
        send(put("/tasks/{id}", taskId), member2, updateBody("Atamasız", "HIGH", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    // ---------- durum değiştirme ----------

    @Test
    void durumDegisinceHedefKolonunSonunaGecerKaynakBoslukKapanir() throws Exception {
        long projectId = newProject();
        long a = createTask(projectId, "A", member);
        createTask(projectId, "B", member);
        createTask(projectId, "C", member);
        long d = createTask(projectId, "D", member);
        send(patch("/tasks/{id}/status", d), member, Map.of("status", "IN_PROGRESS")).andExpect(status().isOk());

        send(patch("/tasks/{id}/status", a), member2, Map.of("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.position").value(1));

        assertThat(board(projectId)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "B", "TODO:0", "C", "TODO:1", "D", "IN_PROGRESS:0", "A", "IN_PROGRESS:1"));

        // Aynı kolona "taşıma" bir şey değiştirmez
        send(patch("/tasks/{id}/status", a), member, Map.of("status", "IN_PROGRESS"))
                .andExpect(jsonPath("$.position").value(1));
        send(patch("/tasks/{id}/status", a), member, Map.of("status", "YOK"))
                .andExpect(status().isBadRequest());
        send(patch("/tasks/{id}/status", a), member, Map.of())
                .andExpect(status().isBadRequest());
    }

    // ---------- silme ----------

    @Test
    void silmeYetkisiReporterOwnerVeAdmin() throws Exception {
        long projectId = newProject();
        long byMember = createTask(projectId, "Üyenin", member);
        long second = createTask(projectId, "İkinci", member);
        long third = createTask(projectId, "Üçüncü", member);
        createTask(projectId, "Dördüncü", member);

        perform(delete("/tasks/{id}", byMember), member2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("TASK_DELETE_FORBIDDEN"));
        perform(get("/tasks/{id}", byMember), member2).andExpect(jsonPath("$.canDelete").value(false));

        perform(delete("/tasks/{id}", byMember), member).andExpect(status().isNoContent());
        perform(delete("/tasks/{id}", second), owner).andExpect(status().isNoContent());
        perform(delete("/tasks/{id}", third), admin).andExpect(status().isNoContent());

        perform(get("/tasks/{id}", byMember), member).andExpect(status().isNotFound());
        assertThat(board(projectId)).as("silinenlerin boşluğu kapandı")
                .containsExactlyInAnyOrderEntriesOf(Map.of("Dördüncü", "TODO:0"));
    }
}
