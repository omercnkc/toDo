package com.kafein.todo.dashboard;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.dashboard.client.ProjectDirectory;
import com.kafein.todo.dashboard.client.ProjectMembership;
import com.kafein.todo.dashboard.client.TaskDirectory;
import com.kafein.todo.dashboard.client.TaskStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 6-B altyapısı: güvenlik zinciri ve Feign client'ların gerçek HTTP çağrısı (sahte project/task-service'e,
 * {@link StubServer}). Servis adresleri Eureka yerine Spring Cloud'un basit keşif istemcisinden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardServiceApplicationTests {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final JwtService JWT = new JwtService(SECRET, Duration.ofMinutes(15));
    private static final StubServer PROJECT_SERVICE = start();
    private static final StubServer TASK_SERVICE = start();
    private static final AuthenticatedUser USER = new AuthenticatedUser(7L, "ayse@ornek.com", Role.USER);

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.project-service[0].uri", PROJECT_SERVICE::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[0].uri", TASK_SERVICE::uri);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProjectDirectory projects;
    @Autowired
    private TaskDirectory tasks;

    private String bearer;

    private static StubServer start() {
        return new StubServer();
    }

    @AfterAll
    static void stop() {
        PROJECT_SERVICE.close();
        TASK_SERVICE.close();
    }

    /** Feign interceptor'ı token'ı o anki HTTP isteğinden alır: isteği simüle et. */
    @BeforeEach
    void simulateIncomingRequest() {
        PROJECT_SERVICE.reset();
        TASK_SERVICE.reset();
        bearer = "Bearer " + JWT.generateAccessToken(USER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- güvenlik ----------

    @Test
    void tokenYoksa401ServisTokeni401SaglikAcik() throws Exception {
        mvc.perform(get("/dashboard/summary")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT.generateServiceToken("project-service")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer bozuk.token.degeri"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // Giriş yapmış kullanıcı: güvenlikten geçer, uç henüz yok (6-C) → ortak hata biçiminde 404
        mvc.perform(get("/dashboard/olmayan").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- project-service ----------

    @Test
    void uyeliklerTokenIletilerekAlinirBilinmeyenAlanlarSorunOlmaz() {
        PROJECT_SERVICE.respond(200, """
                [{"projectId":3,"projectName":"Web Sitesi","userId":7,"role":"OWNER","projectStatus":"ACTIVE","yeniAlan":1},
                 {"projectId":5,"projectName":"Arşiv","userId":7,"role":"MEMBER","projectStatus":"ARCHIVED"}]""");

        List<ProjectMembership> result = projects.memberships(7);

        assertThat(result).containsExactly(
                new ProjectMembership(3L, "Web Sitesi", 7L, "OWNER", "ACTIVE"),
                new ProjectMembership(5L, "Arşiv", 7L, "MEMBER", "ARCHIVED"));
        assertThat(result.get(1).isArchived()).isTrue();
        StubServer.Received call = PROJECT_SERVICE.received().get(0);
        assertThat(call.pathAndQuery()).isEqualTo("/internal/projects/memberships?userId=7");
        assertThat(call.authorization()).as("kullanıcının token'ı iletilir").isEqualTo(bearer);
    }

    @Test
    void projeServisiHatasiZamanAsimiVeKapaliysa503() {
        PROJECT_SERVICE.respond(500, "{}");
        assertUnavailable(() -> projects.memberships(7), "PROJECT_SERVICE_UNAVAILABLE");

        PROJECT_SERVICE.respond(200, "[]");
        PROJECT_SERVICE.delay(1500); // read-timeout 800 ms
        assertUnavailable(() -> projects.memberships(7), "PROJECT_SERVICE_UNAVAILABLE");
        PROJECT_SERVICE.delay(0);

        PROJECT_SERVICE.respond(404, "{}"); // beklenmeyen 4xx de veri yok demek değildir
        assertUnavailable(() -> projects.memberships(7), "PROJECT_SERVICE_UNAVAILABLE");
    }

    // ---------- task-service ----------

    @Test
    void istatistiklerKapsamlaAlinir() {
        TASK_SERVICE.respond(200, """
                {"scope":"mine","projects":[{"projectId":3,"todo":2,"inProgress":1,"done":4,"overdue":1,"total":7}],
                 "assignedToMe":{"open":3,"overdue":1}}""");

        TaskStats stats = tasks.stats("mine");

        assertThat(stats.projects()).containsExactly(new TaskStats.ProjectTaskStats(3, 2, 1, 4, 1, 7));
        assertThat(stats.assignedToMe()).isEqualTo(new TaskStats.AssignedToMe(3, 1));
        assertThat(TASK_SERVICE.received().get(0).pathAndQuery()).isEqualTo("/tasks/stats?scope=mine");
        assertThat(TASK_SERVICE.received().get(0).authorization()).isEqualTo(bearer);
    }

    @Test
    void gorevServisi403KapsamYasagiDigerHatalar503() {
        TASK_SERVICE.respond(403, "{\"error\":\"STATS_SCOPE_FORBIDDEN\"}");
        assertThatThrownBy(() -> tasks.stats("all")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(403);
            assertThat(e.getError()).isEqualTo("STATS_SCOPE_FORBIDDEN");
        });

        TASK_SERVICE.respond(503, "{}");
        assertUnavailable(() -> tasks.stats("mine"), "TASK_SERVICE_UNAVAILABLE");
    }

    @Test
    void kapaliServis503() {
        // Port açık değil: bağlantı reddi de 503 (sunucu bir sonraki testte reset() ile açılır)
        TASK_SERVICE.stop();
        assertUnavailable(() -> tasks.stats("mine"), "TASK_SERVICE_UNAVAILABLE");
        PROJECT_SERVICE.stop();
        assertUnavailable(() -> projects.memberships(7), "PROJECT_SERVICE_UNAVAILABLE");
    }

    private static void assertUnavailable(Runnable call, String error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(503);
            assertThat(e.getError()).isEqualTo(error);
        });
    }
}
