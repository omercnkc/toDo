package com.kafein.todo.task.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.task.IntegrationTestSupport;
import com.kafein.todo.task.task.Task;
import com.kafein.todo.task.task.TaskPriority;
import com.kafein.todo.task.task.TaskStatus;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /tasks/stats} (Faz 6-A): sayıların DB ve liste ucuyla tutarlılığı, kapsam ve yetki. */
class TaskStatsIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Istanbul"));

    @Autowired
    private JdbcTemplate jdbc;

    private Task seed(long projectId, TaskStatus status, Long assigneeId, LocalDate dueDate) {
        Task task = new Task(projectId, "Görev", null, TaskPriority.MEDIUM, assigneeId, owner.id(), dueDate, 0);
        task.placeAt(status, 0);
        return tasks.saveAndFlush(task);
    }

    private JsonNode stats(AuthenticatedUser as, String... scope) throws Exception {
        var request = get("/tasks/stats");
        if (scope.length > 0) {
            request.param("scope", scope[0]);
        }
        return json(perform(request, as).andExpect(status().isOk()));
    }

    private static Map<Long, JsonNode> byProject(JsonNode body) {
        Map<Long, JsonNode> result = new HashMap<>();
        body.get("projects").forEach(p -> result.put(p.get("projectId").asLong(), p));
        return result;
    }

    @Test
    void uyeOlunanProjelerDurumGecikmisVeBanaAtananSayilari() throws Exception {
        long first = newProject();
        long second = newProject();
        long empty = newProject();
        archive(second);
        long foreign = newProject(member2, stranger); // owner üye değil

        seed(first, TaskStatus.TODO, owner.id(), TODAY.minusDays(3));     // gecikmiş, bana atanmış
        seed(first, TaskStatus.TODO, null, TODAY);                        // bugün: gecikmiş değil
        seed(first, TaskStatus.IN_PROGRESS, owner.id(), TODAY.plusDays(2)); // bana atanmış, açık
        seed(first, TaskStatus.IN_PROGRESS, member.id(), TODAY.minusDays(1)); // gecikmiş
        seed(first, TaskStatus.DONE, owner.id(), TODAY.minusDays(9));     // bitti: gecikmiş/açık sayılmaz
        seed(second, TaskStatus.TODO, owner.id(), null);                  // arşivdeki proje, açık
        seed(second, TaskStatus.DONE, null, null);
        seed(foreign, TaskStatus.TODO, owner.id(), TODAY.minusDays(1));   // kapsam dışı

        JsonNode body = stats(owner);
        assertThat(body.get("scope").asText()).isEqualTo("mine");
        Map<Long, JsonNode> projects = byProject(body);
        assertThat(projects).containsOnlyKeys(first, second, empty);

        JsonNode p1 = projects.get(first);
        assertThat(p1.get("todo").asLong()).isEqualTo(2);
        assertThat(p1.get("inProgress").asLong()).isEqualTo(2);
        assertThat(p1.get("done").asLong()).isEqualTo(1);
        assertThat(p1.get("overdue").asLong()).isEqualTo(2);
        assertThat(p1.get("total").asLong()).isEqualTo(5);
        assertThat(projects.get(second).get("total").asLong()).isEqualTo(2);
        assertThat(projects.get(empty).get("total").asLong()).as("görevsiz proje sıfırla").isZero();

        assertThat(body.get("assignedToMe").get("open").asLong()).as("first'te 2 + arşivdeki 1; yabancı proje hariç").isEqualTo(3);
        assertThat(body.get("assignedToMe").get("overdue").asLong()).isEqualTo(1);
    }

    @Test
    void sayilarDbVeFiltreliListeUcuylaTutarli() throws Exception {
        long a = newProject();
        long b = newProject();
        Random random = new Random(6);
        Long[] assignees = {null, owner.id(), member.id()};
        for (int i = 0; i < 150; i++) {
            LocalDate due = random.nextInt(4) == 0 ? null : TODAY.plusDays(random.nextInt(21) - 10);
            seed(random.nextBoolean() ? a : b, TaskStatus.values()[random.nextInt(3)],
                    assignees[random.nextInt(3)], due);
        }

        Map<Long, JsonNode> projects = byProject(stats(owner));
        for (long projectId : new long[]{a, b}) {
            JsonNode p = projects.get(projectId);
            for (TaskStatus status : TaskStatus.values()) {
                long db = jdbc.queryForObject("select count(*) from tasks where project_id = ? and status = ?",
                        Long.class, projectId, status.getCode());
                String field = switch (status) {
                    case TODO -> "todo";
                    case IN_PROGRESS -> "inProgress";
                    case DONE -> "done";
                };
                assertThat(p.get(field).asLong()).as("%s %s", projectId, status).isEqualTo(db);
                assertThat(listTotal(projectId, "status", status.name())).isEqualTo(db);
            }
            long overdueDb = jdbc.queryForObject("select count(*) from tasks where project_id = ? and status <> 2"
                    + " and due_date < ?", Long.class, projectId, TODAY);
            assertThat(p.get("overdue").asLong()).isEqualTo(overdueDb);
            assertThat(listTotal(projectId, "overdue", "true")).as("liste ucunun gecikmiş filtresiyle aynı").isEqualTo(overdueDb);
        }
        long myOpenDb = jdbc.queryForObject("select count(*) from tasks where project_id in (?, ?) and assignee_id = ?"
                + " and status <> 2", Long.class, a, b, owner.id());
        JsonNode mine = stats(owner).get("assignedToMe");
        assertThat(mine.get("open").asLong()).isEqualTo(myOpenDb);
        assertThat(mine.get("open").asLong()).isEqualTo(json(perform(get("/tasks").param("assigneeId", String.valueOf(owner.id()))
                .param("status", "TODO,IN_PROGRESS"), owner)).get("totalElements").asLong());
    }

    private long listTotal(long projectId, String param, String value) throws Exception {
        return json(perform(get("/tasks").param("projectId", String.valueOf(projectId)).param(param, value), owner))
                .get("totalElements").asLong();
    }

    @Test
    void adminTumSistemiGorurKullaniciGoremez() throws Exception {
        long projectId = newProject(member2); // admin de owner da üye değil
        seed(projectId, TaskStatus.TODO, admin.id(), TODAY.minusDays(1));
        seed(projectId, TaskStatus.DONE, null, null);

        JsonNode all = stats(admin, "all");
        assertThat(all.get("scope").asText()).isEqualTo("all");
        JsonNode p = byProject(all).get(projectId);
        assertThat(p.get("total").asLong()).isEqualTo(2);
        assertThat(p.get("overdue").asLong()).isEqualTo(1);
        long allTotal = 0;
        for (JsonNode node : all.get("projects")) {
            allTotal += node.get("total").asLong();
        }
        assertThat(allTotal).as("tüm sistemin toplamı tablo boyuyla aynı")
                .isEqualTo(jdbc.queryForObject("select count(*) from tasks", Long.class));
        assertThat(all.get("assignedToMe").get("open").asLong()).isGreaterThanOrEqualTo(1);

        assertThat(byProject(stats(admin))).as("ADMIN'in varsayılanı yine üye olduğu projeler").doesNotContainKey(projectId);
        perform(get("/tasks/stats").param("scope", "all"), owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("STATS_SCOPE_FORBIDDEN"));
        perform(get("/tasks/stats").param("scope", "hepsi"), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scope"));
        perform(get("/tasks/stats").param("scope", "ALL"), admin).andExpect(status().isOk());
    }

    @Test
    void uyeligiOlmayanBosYanitProjeServisiKapaliyken503() throws Exception {
        JsonNode body = stats(stranger);
        assertThat(body.get("projects")).isEmpty();
        assertThat(body.get("assignedToMe").get("open").asLong()).isZero();
        mvc.perform(get("/tasks/stats")).andExpect(status().isUnauthorized());

        newProject();
        Request request = Request.create(Request.HttpMethod.GET, "http://project-service", Map.of(), null,
                StandardCharsets.UTF_8, null);
        doThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request))
                .when(projectClient).memberships(anyLong());
        perform(get("/tasks/stats"), owner).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROJECT_SERVICE_UNAVAILABLE"));
        // tüm sistem kapsamı project-service'e sormaz
        perform(get("/tasks/stats").param("scope", "all"), admin).andExpect(status().isOk());
    }
}
