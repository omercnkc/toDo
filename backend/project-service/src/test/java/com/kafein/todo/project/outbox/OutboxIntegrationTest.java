package com.kafein.todo.project.outbox;

import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.ServicePrincipal;
import com.kafein.todo.project.IntegrationTestSupport;
import com.kafein.todo.project.client.TaskClient;
import feign.FeignException;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kalıcı silme / üye çıkarma → outbox kaydı, ve relay'in teslim/yeniden deneme/temizlik davranışı. */
class OutboxIntegrationTest extends IntegrationTestSupport {

    private static final Request FEIGN_REQUEST = Request.create(Request.HttpMethod.DELETE,
            "http://task-service/internal/tasks/by-project/1", Map.of(), null, StandardCharsets.UTF_8, null);

    @Autowired
    private OutboxEventRepository events;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JwtService jwtService;

    private TransactionTemplate tx;

    @BeforeEach
    void isolateOutbox() {
        tx = new TransactionTemplate(transactionManager);
        // Diğer testlerin bıraktığı bekleyen kayıtlar bu testin relay çağrılarına karışmasın
        jdbc.update("UPDATE outbox_events SET status = 1, processed_at = UTC_TIMESTAMP(6) WHERE status = 0");
    }

    private OutboxEvent save(OutboxEvent event) {
        return events.saveAndFlush(event);
    }

    private OutboxEvent reload(OutboxEvent event) {
        return events.findById(event.getId()).orElseThrow();
    }

    private void update(OutboxEvent event, Consumer<OutboxEvent> change) {
        tx.executeWithoutResult(status -> change.accept(events.findById(event.getId()).orElseThrow()));
    }

    /** Yeniden deneme zamanını beklemeden kaydı hemen işlenebilir yapar. */
    private void makeDue(OutboxEvent event) {
        update(event, e -> e.claimUntil(Instant.now().minusSeconds(1)));
    }

    // ---------- olayların yazılması ----------

    @Test
    void kaliciSilmeProjeyiSilerVeOutboxaYazar() throws Exception {
        long id = createProject("Kalıcı silinecek", true);
        send(post("/projects/" + id + "/invitations"), owner, Map.of("userId", stranger.id()))
                .andExpect(status().isCreated());

        perform(delete("/projects/" + id).param("permanent", "true"), owner).andExpect(status().isNoContent());

        assertThat(projects.existsById(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_invitations WHERE project_id = ?",
                Integer.class, id)).isZero();
        perform(get("/projects/" + id), owner).andExpect(status().isNotFound());

        List<OutboxEvent> written = events.findByAggregateIdOrderByIdAsc(id);
        assertThat(written).hasSize(1);
        OutboxEvent event = written.get(0);
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.PROJECT_DELETED);
        assertThat(event.getAggregateType()).isEqualTo("PROJECT");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    }

    @Test
    void kaliciSilmeYetkisi() throws Exception {
        long id = createProject("Korunan", true);

        perform(delete("/projects/" + id).param("permanent", "true"), member).andExpect(status().isForbidden());
        perform(delete("/projects/" + id).param("permanent", "true"), stranger).andExpect(status().isForbidden());
        assertThat(projects.existsById(id)).isTrue();
        assertThat(events.findByAggregateIdOrderByIdAsc(id)).isEmpty();

        // Arşivdeki proje de kalıcı silinebilir; ADMIN de silebilir
        perform(delete("/projects/" + id), owner).andExpect(status().isNoContent());
        perform(delete("/projects/" + id).param("permanent", "true"), admin).andExpect(status().isNoContent());
        assertThat(projects.existsById(id)).isFalse();

        perform(delete("/projects/" + id).param("permanent", "true"), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"));
        assertThat(events.findByAggregateIdOrderByIdAsc(id)).as("ikinci silme olay yazmaz").hasSize(1);
    }

    @Test
    void uyeCikarmaAtamaTemizligiOlayiYazar() throws Exception {
        long id = createProject("Üyeli", true);

        perform(delete("/projects/" + id + "/members/" + member.id()), owner).andExpect(status().isNoContent());

        List<OutboxEvent> written = events.findByAggregateIdOrderByIdAsc(id);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getEventType()).isEqualTo(OutboxEventType.MEMBER_REMOVED);
        assertThat(written.get(0).payloadUserId()).isEqualTo(member.id());
        assertThat(jdbc.queryForObject("SELECT JSON_EXTRACT(payload, '$.userId') FROM outbox_events WHERE id = ?",
                Long.class, written.get(0).getId())).isEqualTo(member.id());
    }

    // ---------- relay ----------

    @Test
    void relayOlaylariTaskServiseIletirVeSentYapar() {
        OutboxEvent deleted = save(OutboxEvent.projectDeleted(501L, Instant.now()));
        OutboxEvent removed = save(OutboxEvent.memberRemoved(502L, 77L, Instant.now()));
        OutboxEvent future = save(OutboxEvent.projectDeleted(503L, Instant.now()));
        update(future, e -> e.claimUntil(Instant.now().plusSeconds(60)));

        assertThat(relay.processBatch()).isEqualTo(2);

        verify(taskClient).deleteProjectTasks(501L);
        verify(taskClient).unassignMember(502L, 77L);
        verify(taskClient, never()).deleteProjectTasks(503L);
        for (OutboxEvent event : List.of(deleted, removed)) {
            OutboxEvent after = reload(event);
            assertThat(after.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(after.getProcessedAt()).isNotNull();
        }
        assertThat(relay.processBatch()).as("tekrar çalışınca gönderilecek bir şey yok").isZero();
    }

    @Test
    void hataDonerseGeriCekilmeyleTekrarDenenirOnDenemedeFailedOlur() {
        doThrow(new FeignException.InternalServerError("patladı", FEIGN_REQUEST, null, Map.of()))
                .when(taskClient).deleteProjectTasks(anyLong());
        OutboxEvent event = save(OutboxEvent.projectDeleted(601L, Instant.now()));

        relay.processBatch();

        OutboxEvent after = reload(event);
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getLastError()).contains("patladı");
        assertThat(after.getNextAttemptAt()).isCloseTo(Instant.now().plusSeconds(10), within(5, ChronoUnit.SECONDS));
        assertThat(relay.processBatch()).as("zamanı gelmeden denenmez").isZero();

        for (int attempt = 2; attempt <= OutboxRelay.MAX_ATTEMPTS; attempt++) {
            makeDue(event);
            relay.processBatch();
        }

        OutboxEvent failed = reload(event);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(OutboxRelay.MAX_ATTEMPTS);
        assertThat(failed.getProcessedAt()).isNotNull();
        verify(taskClient, times(OutboxRelay.MAX_ATTEMPTS)).deleteProjectTasks(601L);
    }

    @Test
    void servisUlasilamazkenSinirsizDenenir() {
        doThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.DELETE, (Long) null, FEIGN_REQUEST))
                .when(taskClient).deleteProjectTasks(anyLong());
        doThrow(new FeignException.ServiceUnavailable("Load balancer does not contain an instance", FEIGN_REQUEST,
                null, Map.of())).when(taskClient).unassignMember(anyLong(), anyLong());
        OutboxEvent down = save(OutboxEvent.projectDeleted(701L, Instant.now()));
        OutboxEvent noInstance = save(OutboxEvent.memberRemoved(702L, 5L, Instant.now()));

        for (int attempt = 1; attempt <= OutboxRelay.MAX_ATTEMPTS + 2; attempt++) {
            makeDue(down);
            makeDue(noInstance);
            relay.processBatch();
        }

        for (OutboxEvent event : List.of(down, noInstance)) {
            OutboxEvent after = reload(event);
            assertThat(after.getStatus()).as("ulaşılamıyorsa FAILED olmaz").isEqualTo(OutboxStatus.PENDING);
            assertThat(after.getAttempts()).isEqualTo(OutboxRelay.MAX_ATTEMPTS + 2);
            assertThat(after.getNextAttemptAt()).as("bekleme 10 dk ile sınırlı")
                    .isCloseTo(Instant.now().plus(Duration.ofMinutes(10)), within(10, ChronoUnit.SECONDS));
        }

        // Servis geri geldi: bir sonraki denemede iletilir
        org.mockito.Mockito.reset(taskClient);
        makeDue(down);
        relay.processBatch();
        assertThat(reload(down).getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void ikiRelayAyniAndaCalisincaHerOlayBirKezIletilir() throws Exception {
        List<Long> projectIds = new ArrayList<>();
        for (long id = 801; id <= 845; id++) {
            save(OutboxEvent.projectDeleted(id, Instant.now()));
            projectIds.add(id);
        }

        Callable<Integer> drain = () -> {
            int total = 0;
            int processed;
            while ((processed = relay.processBatch()) > 0) {
                total += processed;
            }
            return total;
        };
        ExecutorService pool = Executors.newFixedThreadPool(3);
        int total = 0;
        try {
            for (Future<Integer> result : pool.invokeAll(List.of(drain, drain, drain))) {
                total += result.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(total).isEqualTo(projectIds.size());
        for (long id : projectIds) {
            verify(taskClient, times(1)).deleteProjectTasks(id);
        }
    }

    @Test
    void temizlikYalnizcaEskiSentKayitlariniSiler() {
        OutboxEvent oldSent = save(OutboxEvent.projectDeleted(901L, Instant.now()));
        update(oldSent, e -> e.markSent(Instant.now().minus(Duration.ofDays(8))));
        OutboxEvent recentSent = save(OutboxEvent.projectDeleted(902L, Instant.now()));
        update(recentSent, e -> e.markSent(Instant.now().minus(Duration.ofDays(6))));
        OutboxEvent oldFailed = save(OutboxEvent.projectDeleted(903L, Instant.now()));
        update(oldFailed, e -> e.markAttemptFailed("x", Instant.now(), true, Instant.now().minus(Duration.ofDays(30))));
        OutboxEvent pending = save(OutboxEvent.projectDeleted(904L, Instant.now()));
        update(pending, e -> e.claimUntil(Instant.now().plusSeconds(60)));

        relay.purgeSent();

        assertThat(events.findById(oldSent.getId())).isEmpty();
        assertThat(events.findById(recentSent.getId())).isPresent();
        assertThat(events.findById(oldFailed.getId())).as("FAILED incelenmek üzere kalır").isPresent();
        assertThat(events.findById(pending.getId())).isPresent();
    }

    @Test
    void geriCekilmeSuresi() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(OutboxRelay.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(OutboxRelay.backoff(6)).isEqualTo(Duration.ofSeconds(320));
        assertThat(OutboxRelay.backoff(7)).isEqualTo(Duration.ofMinutes(10));
        assertThat(OutboxRelay.backoff(1_000)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void taskClientServisTokeniGonderir() {
        RequestInterceptor interceptor = new TaskClient.ServiceTokenConfig()
                .serviceTokenInterceptor(jwtService, "project-service");
        RequestTemplate template = new RequestTemplate();
        template.header(HttpHeaders.AUTHORIZATION, "Bearer kullanici-token");

        interceptor.apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).hasSize(1);
        String token = template.headers().get(HttpHeaders.AUTHORIZATION).iterator().next().substring("Bearer ".length());
        assertThat(jwtService.parseServiceToken(token)).isEqualTo(new ServicePrincipal("project-service"));
    }
}
