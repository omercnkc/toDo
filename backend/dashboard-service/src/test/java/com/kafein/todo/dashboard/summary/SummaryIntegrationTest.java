package com.kafein.todo.dashboard.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.dashboard.DashboardTestSupport;
import com.kafein.todo.dashboard.StubServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /dashboard/summary} (6-C): birleştirme, kapsam, önbellek, hata davranışı. */
class SummaryIntegrationTest extends DashboardTestSupport {

    private static final String MEMBERSHIPS = "/internal/projects/memberships";
    private static final String ALL_PROJECTS = "/internal/projects/all";
    private static final String STATS = "/tasks/stats";

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions perform(MockHttpServletRequestBuilder request, AuthenticatedUser as) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(as)));
    }

    private JsonNode summary(AuthenticatedUser as, String... scope) throws Exception {
        MockHttpServletRequestBuilder request = get("/dashboard/summary");
        if (scope.length > 0) {
            request.param("scope", scope[0]);
        }
        String body = perform(request, as).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static long calls(StubServer server, String path) {
        return server.received().stream().filter(r -> r.pathAndQuery().startsWith(path)).count();
    }

    /** Üyelikler: Web (OWNER, aktif), Arşiv (MEMBER, arşivde), boş proje (aktif), çeltik (aktif). */
    private static void myProjects(AuthenticatedUser user) {
        PROJECT_SERVICE.route(MEMBERSHIPS, 200, """
                [{"projectId":3,"projectName":"Web Sitesi","userId":%1$d,"role":"OWNER","projectStatus":"ACTIVE"},
                 {"projectId":5,"projectName":"Arşiv","userId":%1$d,"role":"MEMBER","projectStatus":"ARCHIVED"},
                 {"projectId":8,"projectName":"boş proje","userId":%1$d,"role":"OWNER","projectStatus":"ACTIVE"},
                 {"projectId":9,"projectName":"Çeltik","userId":%1$d,"role":"MEMBER","projectStatus":"ACTIVE"}]"""
                .formatted(user.id()));
        TASK_SERVICE.route(STATS, 200, """
                {"scope":"mine","projects":[
                  {"projectId":3,"todo":2,"inProgress":1,"done":3,"overdue":1,"total":6},
                  {"projectId":5,"todo":0,"inProgress":0,"done":4,"overdue":0,"total":4},
                  {"projectId":9,"todo":1,"inProgress":1,"done":1,"overdue":2,"total":3},
                  {"projectId":77,"todo":50,"inProgress":0,"done":0,"overdue":9,"total":50}],
                 "assignedToMe":{"open":4,"overdue":1}}""");
    }

    @Test
    void projelerVeSayilarBirlestirilirToplamlarListedekiProjelerden() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        myProjects(user);

        JsonNode s = summary(user);

        assertThat(s.get("scope").asText()).isEqualTo("mine");
        assertThat(s.get("projectCount").asInt()).isEqualTo(4);
        assertThat(s.get("activeProjectCount").asInt()).isEqualTo(3);
        assertThat(s.get("archivedProjectCount").asInt()).isEqualTo(1);
        // proje 77 listede yok (ör. silinmiş, görevleri henüz temizlenmemiş): sayılmaz
        assertThat(s.get("taskTotals").toString()).isEqualTo("{\"todo\":3,\"inProgress\":2,\"done\":8}");
        assertThat(s.get("totalTasks").asLong()).isEqualTo(13);
        assertThat(s.get("openTasks").asLong()).isEqualTo(5);
        assertThat(s.get("overdueCount").asLong()).isEqualTo(3);
        assertThat(s.get("assignedToMe").toString()).isEqualTo("{\"open\":4,\"overdue\":1}");
        assertThat(s.get("generatedAt").asText()).isNotBlank();

        List<String> order = new ArrayList<>();
        s.get("projects").forEach(p -> order.add(p.get("name").asText()));
        assertThat(order).as("aktifler önce, Türkçe ada göre (b < ç < W), arşiv sonda")
                .containsExactly("boş proje", "Çeltik", "Web Sitesi", "Arşiv");
        JsonNode web = s.get("projects").get(2);
        assertThat(web.get("myRole").asText()).isEqualTo("OWNER");
        assertThat(web.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(web.get("total").asLong()).isEqualTo(6);
        assertThat(web.get("progressPercent").asInt()).isEqualTo(50);
        assertThat(s.get("projects").get(0).get("progressPercent").asInt()).as("görevsiz proje").isZero();
        assertThat(s.get("projects").get(1).get("progressPercent").asInt()).as("1/3 aşağı yuvarlanır").isEqualTo(33);
        assertThat(s.get("projects").get(3).get("progressPercent").asInt()).isEqualTo(100);
    }

    @Test
    void ikiServiseDeKullaniciTokeniIletilirParalelCagrilarda() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        myProjects(user);
        String expected = bearer(user);

        mvc.perform(get("/dashboard/summary").header(HttpHeaders.AUTHORIZATION, expected)).andExpect(status().isOk());

        StubServer.Received membership = PROJECT_SERVICE.received().get(0);
        StubServer.Received stats = TASK_SERVICE.received().get(0);
        assertThat(membership.pathAndQuery()).isEqualTo(MEMBERSHIPS + "?userId=" + user.id());
        assertThat(stats.pathAndQuery()).isEqualTo(STATS + "?scope=mine");
        assertThat(membership.authorization()).isEqualTo(expected);
        assertThat(stats.authorization()).as("sanal thread'de de istek bağlamı var").isEqualTo(expected);
    }

    @Test
    void uyeligiOlmayanBosOzet() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        TASK_SERVICE.route(STATS, 200, "{\"scope\":\"mine\",\"projects\":[],\"assignedToMe\":{\"open\":0,\"overdue\":0}}");

        JsonNode s = summary(user);

        assertThat(s.get("projectCount").asInt()).isZero();
        assertThat(s.get("totalTasks").asLong()).isZero();
        assertThat(s.get("projects")).isEmpty();
    }

    @Test
    void adminTumSistemiGorurKullaniciyaYasakVeAsagiCagriYapilmaz() throws Exception {
        AuthenticatedUser admin = newUser(Role.ADMIN);
        PROJECT_SERVICE.route(ALL_PROJECTS, 200, """
                [{"id":3,"name":"Web Sitesi","status":"ACTIVE"},{"id":4,"name":"Başkasının","status":"ACTIVE"}]""");
        TASK_SERVICE.route(STATS, 200, """
                {"scope":"all","projects":[{"projectId":4,"todo":1,"inProgress":0,"done":1,"overdue":0,"total":2}],
                 "assignedToMe":{"open":0,"overdue":0}}""");

        JsonNode s = summary(admin, "all");

        assertThat(s.get("scope").asText()).isEqualTo("all");
        assertThat(s.get("projectCount").asInt()).isEqualTo(2);
        assertThat(s.get("totalTasks").asLong()).isEqualTo(2);
        assertThat(s.get("projects").get(0).get("name").asText()).isEqualTo("Başkasının");
        assertThat(s.get("projects").get(0).get("myRole").isNull()).isTrue();
        assertThat(calls(TASK_SERVICE, STATS + "?scope=all")).isEqualTo(1);
        assertThat(calls(PROJECT_SERVICE, MEMBERSHIPS)).as("tüm sistemde üyeliklere bakılmaz").isZero();

        AuthenticatedUser user = newUser(Role.USER);
        PROJECT_SERVICE.received().clear();
        TASK_SERVICE.received().clear();
        perform(get("/dashboard/summary").param("scope", "all"), user).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("STATS_SCOPE_FORBIDDEN"));
        assertThat(PROJECT_SERVICE.received()).isEmpty();
        assertThat(TASK_SERVICE.received()).isEmpty();
        perform(get("/dashboard/summary").param("scope", "hepsi"), user).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scope"));
    }

    @Test
    void onbellekKullaniciVeKapsamBasinaHatalarOnbelleğeAlinmaz() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        myProjects(user);

        String first = summary(user).get("generatedAt").asText();
        String second = summary(user).get("generatedAt").asText();
        assertThat(second).as("ikinci istek önbellekten").isEqualTo(first);
        assertThat(calls(PROJECT_SERVICE, MEMBERSHIPS)).isEqualTo(1);
        assertThat(calls(TASK_SERVICE, STATS)).isEqualTo(1);

        AuthenticatedUser other = newUser(Role.USER);
        myProjects(other);
        summary(other);
        assertThat(calls(TASK_SERVICE, STATS)).as("başka kullanıcı kendi özetini alır").isEqualTo(2);

        // Aynı kullanıcı ADMIN olunca (yeni token) eski kapsamla yanıt almaz
        AuthenticatedUser promoted = new AuthenticatedUser(user.id(), user.email(), Role.ADMIN);
        summary(promoted);
        assertThat(calls(TASK_SERVICE, STATS)).isEqualTo(3);

        AuthenticatedUser failing = newUser(Role.USER);
        TASK_SERVICE.route(STATS, 503, "{}");
        perform(get("/dashboard/summary"), failing).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("TASK_SERVICE_UNAVAILABLE"));
        myProjects(failing);
        assertThat(summary(failing).get("totalTasks").asLong()).as("hata önbelleğe alınmadı").isEqualTo(13);
    }

    @Test
    void freshOnbellegiAtlarVeYeniOzetiOnbellegeYazar() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        myProjects(user);
        String cachedAt = summary(user).get("generatedAt").asText();
        assertThat(calls(TASK_SERVICE, STATS)).isEqualTo(1);

        Thread.sleep(5); // generatedAt farklı olsun
        String body = perform(get("/dashboard/summary").param("fresh", "true"), user).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String freshAt = objectMapper.readTree(body).get("generatedAt").asText();
        assertThat(freshAt).as("önbellek okunmadı").isNotEqualTo(cachedAt);
        assertThat(calls(TASK_SERVICE, STATS)).isEqualTo(2);

        assertThat(summary(user).get("generatedAt").asText()).as("taze özet önbelleğe yazıldı").isEqualTo(freshAt);
        assertThat(calls(TASK_SERVICE, STATS)).isEqualTo(2);
    }

    @Test
    void birTarafKapaliysaKismiDegil503() throws Exception {
        AuthenticatedUser user = newUser(Role.USER);
        myProjects(user);
        PROJECT_SERVICE.stop();
        perform(get("/dashboard/summary"), user).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROJECT_SERVICE_UNAVAILABLE"));

        PROJECT_SERVICE.start();
        TASK_SERVICE.stop();
        perform(get("/dashboard/summary"), user).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("TASK_SERVICE_UNAVAILABLE"));
    }
}
