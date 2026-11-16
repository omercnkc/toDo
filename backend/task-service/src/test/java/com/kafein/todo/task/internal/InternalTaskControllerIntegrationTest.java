package com.kafein.todo.task.internal;

import com.kafein.todo.task.IntegrationTestSupport;
import com.kafein.todo.task.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dahili uçlar: yalnızca servis token'ı, idempotent, her durumda 204 (docs/06 "Dahili Uçlar"). */
class InternalTaskControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private void asService(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request.header(HttpHeaders.AUTHORIZATION, serviceBearer())).andExpect(status().isNoContent());
    }

    private long createAssigned(long projectId, String title, long assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", projectId);
        body.put("title", title);
        body.put("assigneeId", assigneeId);
        return json(send(post("/tasks"), member, body).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void projeninTumGorevleriSilinirDigerProjelerEtkilenmez() throws Exception {
        long projectId = newProject();
        long otherProject = newProject();
        createTask(projectId, "A", member);
        createTask(projectId, "B", member);
        long kept = createTask(otherProject, "Kalan", member);

        asService(delete("/internal/tasks/by-project/{id}", projectId));

        assertThat(tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId)).isEmpty();
        assertThat(tasks.findById(kept)).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_board_locks WHERE project_id = ?", Integer.class,
                projectId)).as("kilit satırı da temizlendi").isZero();

        // Tekrar (relay en az bir kez teslim eder) ve hiç görevi olmayan proje: yine 204
        asService(delete("/internal/tasks/by-project/{id}", projectId));
        asService(delete("/internal/tasks/by-project/{id}", 999_999_999L));
    }

    @Test
    void cikarilanUyeninYalnizcaOProjedekiAtamalariKaldirilir() throws Exception {
        long projectId = newProject();
        long otherProject = newProject();
        long a = createAssigned(projectId, "A", member2.id());
        long b = createAssigned(projectId, "B", member2.id());
        long c = createAssigned(projectId, "C", owner.id());
        long elsewhere = createAssigned(otherProject, "Başka", member2.id());
        long versionBefore = tasks.findById(a).orElseThrow().getVersion();

        asService(delete("/internal/tasks/by-project/{projectId}/assignees/{userId}", projectId, member2.id()));

        assertThat(tasks.findAllById(List.of(a, b))).allSatisfy(task -> assertThat(task.getAssigneeId()).isNull());
        assertThat(tasks.findById(a).orElseThrow().getVersion()).isEqualTo(versionBefore + 1);
        assertThat(tasks.findById(c).orElseThrow().getAssigneeId()).isEqualTo(owner.id());
        assertThat(tasks.findById(elsewhere).orElseThrow().getAssigneeId()).isEqualTo(member2.id());
        assertThat(tasks.findAllById(List.of(a, b, c))).extracting(Task::getTitle).containsExactlyInAnyOrder("A", "B", "C");

        // Atanmış görevi kalmayan kişi için tekrar: yine 204; görev normal düzenlenmeye devam eder
        asService(delete("/internal/tasks/by-project/{projectId}/assignees/{userId}", projectId, member2.id()));
        send(put("/tasks/{id}", a), member, Map.of("title", "A2", "priority", "LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A2"));
    }

    @Test
    void dahiliUclarKullaniciTokeniniKabulEtmez() throws Exception {
        long projectId = newProject();
        createTask(projectId, "A", member);

        perform(delete("/internal/tasks/by-project/{id}", projectId), owner)
                .andExpect(status().isForbidden());
        perform(delete("/internal/tasks/by-project/{id}", projectId), admin)
                .andExpect(status().isForbidden());
        mvc.perform(delete("/internal/tasks/by-project/{id}", projectId))
                .andExpect(status().isUnauthorized());
        assertThat(tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId)).hasSize(1);
    }

    @Test
    void servisTokeniDisUclardaGecersiz() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "A", member);

        mvc.perform(get("/tasks/{id}", taskId).header(HttpHeaders.AUTHORIZATION, serviceBearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/tasks/{id}", taskId).header(HttpHeaders.AUTHORIZATION, serviceBearer()))
                .andExpect(status().isUnauthorized());
        assertThat(tasks.findById(taskId)).isPresent();
    }
}
