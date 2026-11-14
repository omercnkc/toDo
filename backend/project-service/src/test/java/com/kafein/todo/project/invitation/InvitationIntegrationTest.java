package com.kafein.todo.project.invitation;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.project.IntegrationTestSupport;
import com.kafein.todo.project.client.UserSummary;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Davet akışı ve docs/06'daki kuralların her biri. auth-service mock (bkz. {@link IntegrationTestSupport}). */
class InvitationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions invite(long projectId, AuthenticatedUser as, long inviteeId) throws Exception {
        return send(post("/projects/" + projectId + "/invitations"), as, Map.of("userId", inviteeId));
    }

    private long inviteOk(long projectId, long inviteeId) throws Exception {
        String json = invite(projectId, owner, inviteeId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void markUser(long userId, boolean exists, boolean enabled) {
        doAnswer(inv -> {
            com.kafein.todo.project.client.AuthClient.LookupRequest request = inv.getArgument(0);
            return request.ids().stream()
                    .filter(id -> id != userId || exists)
                    .map(id -> id == userId ? new UserSummary(id, "x@ornek.com", "Özel", enabled) : fakeUser(id))
                    .toList();
        }).when(authClient).lookup(any());
    }

    // ---------- mutlu yol ----------

    @Test
    void davetGonderilirKabulEdilinceUyeOlur() throws Exception {
        long projectId = createProject("Davetli proje", false);

        invite(projectId, owner, stranger.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.project.id").value(projectId))
                .andExpect(jsonPath("$.project.name").value("Davetli proje"))
                .andExpect(jsonPath("$.inviter.id").value(owner.id()))
                .andExpect(jsonPath("$.inviter.fullName").value("Kullanıcı " + owner.id()))
                .andExpect(jsonPath("$.invitee.id").value(stranger.id()))
                .andExpect(jsonPath("$.invitee.email").value("kullanici" + stranger.id() + "@ornek.com"))
                .andExpect(jsonPath("$.createdAt").exists());

        // Kabulden önce üye değil
        perform(get("/projects/" + projectId), stranger).andExpect(status().isForbidden());

        String mine = perform(get("/invitations"), stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].project.name").value("Davetli proje"))
                .andReturn().getResponse().getContentAsString();
        long invitationId = objectMapper.readTree(mine).get(0).get("id").asLong();

        perform(post("/invitations/" + invitationId + "/accept"), stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").exists());

        perform(get("/projects/" + projectId), stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));
        perform(get("/invitations"), stranger).andExpect(jsonPath("$.length()").value(0));
        perform(get("/invitations?status=ACCEPTED"), stranger).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reddedilinceUyeOlmaz24SaatYenidenDavetEdilemez() throws Exception {
        long projectId = createProject("Red", false);
        long invitationId = inviteOk(projectId, stranger.id());

        perform(post("/invitations/" + invitationId + "/reject"), stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(members.existsByProjectIdAndUserId(projectId, stranger.id())).isFalse();

        invite(projectId, owner, stranger.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVITATION_RECENTLY_REJECTED"));

        // 24 saatten eski red engel olmaz
        jdbc.update("UPDATE project_invitations SET responded_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 25 HOUR) WHERE id = ?",
                invitationId);
        invite(projectId, owner, stranger.id()).andExpect(status().isCreated());
    }

    // ---------- gönderme kuralları ----------

    @Test
    void kendiniUyeyiVeBekleyeniDavetEdemezsin() throws Exception {
        long projectId = createProject("Kurallar", true);

        invite(projectId, owner, owner.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CANNOT_INVITE_SELF"));
        invite(projectId, owner, member.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_MEMBER"));

        inviteOk(projectId, stranger.id());
        invite(projectId, admin, stranger.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void olmayanVePasifKullaniciDavetEdilemez() throws Exception {
        long projectId = createProject("Kullanıcı kuralları", false);

        markUser(stranger.id(), false, true);
        invite(projectId, owner, stranger.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));

        markUser(stranger.id(), true, false);
        invite(projectId, owner, stranger.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("USER_DISABLED"));
        assertThat(invitationCount(projectId)).isZero();
    }

    @Test
    void arsivlenmisProjeyeDavetGonderilemezVeKabulEdilemez() throws Exception {
        long projectId = createProject("Arşiv daveti", false);
        long invitationId = inviteOk(projectId, stranger.id());
        perform(delete("/projects/" + projectId), owner).andExpect(status().isNoContent());

        invite(projectId, owner, newUser(Role.USER).id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));
        perform(post("/invitations/" + invitationId + "/accept"), stranger)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));

        // Reddetmek serbest; arşivden çıkınca bekleyen davet yeniden kabul edilebilir hale gelir
        send(put("/projects/" + projectId), owner, Map.of("name", "Arşiv daveti", "status", "ACTIVE"));
        perform(post("/invitations/" + invitationId + "/accept"), stranger).andExpect(status().isOk());
    }

    @Test
    void projedeEnFazla50BekleyenDavet() throws Exception {
        long projectId = createProject("Kalabalık", false);
        for (int i = 0; i < InvitationService.MAX_PENDING_PER_PROJECT; i++) {
            jdbc.update("""
                    INSERT INTO project_invitations (project_id, invitee_id, inviter_id, status, created_at)
                    VALUES (?, ?, ?, 0, UTC_TIMESTAMP(6))""", projectId, 5_000_000L + i, owner.id());
        }

        invite(projectId, owner, stranger.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TOO_MANY_PENDING_INVITATIONS"));
    }

    @Test
    void davetGondermeYetkisi() throws Exception {
        long projectId = createProject("Yetki", true);

        invite(projectId, member, stranger.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_MANAGE_DENIED"));
        invite(projectId, stranger, newUser(Role.USER).id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        invite(projectId, admin, stranger.id()).andExpect(status().isCreated());
        send(post("/projects/" + projectId + "/invitations"), owner, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("userId"));
    }

    @Test
    void authServisKapaliysaDavet503VeKayitYok() throws Exception {
        long projectId = createProject("Kesinti", false);
        Request request = Request.create(Request.HttpMethod.POST, "http://auth-service/internal/users/lookup",
                Map.of(), null, StandardCharsets.UTF_8, null);
        doThrow(new FeignException.ServiceUnavailable("kapalı", request, null, Map.of()))
                .when(authClient).lookup(any());

        invite(projectId, owner, stranger.id())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("USER_SERVICE_UNAVAILABLE"));
        assertThat(invitationCount(projectId)).isZero();
    }

    @Test
    void ayniAndaIkiDavettenYalnizcaBiriKaydedilir() throws Exception {
        long projectId = createProject("Yarış", false);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            start.await();
            return invite(projectId, owner, stranger.id()).andReturn().getResponse().getStatus();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>(List.of(pool.submit(call), pool.submit(call)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(invitationCount(projectId)).isEqualTo(1);
    }

    // ---------- yanıtlama kuralları ----------

    @Test
    void davetiYalnizcaDavetEdilenYanitlarBirKez() throws Exception {
        long projectId = createProject("Yanıt", false);
        long invitationId = inviteOk(projectId, stranger.id());

        for (AuthenticatedUser other : List.of(owner, admin, member)) {
            perform(post("/invitations/" + invitationId + "/accept"), other)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("INVITATION_FORBIDDEN"));
        }
        perform(post("/invitations/" + invitationId + "/reject"), admin).andExpect(status().isForbidden());

        perform(post("/invitations/" + invitationId + "/accept"), stranger).andExpect(status().isOk());
        perform(post("/invitations/" + invitationId + "/accept"), stranger)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVITATION_NOT_PENDING"));
        perform(post("/invitations/" + invitationId + "/reject"), stranger).andExpect(status().isConflict());
        perform(post("/invitations/999999999/accept"), stranger)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVITATION_NOT_FOUND"));
    }

    // ---------- proje tarafı: liste ve iptal ----------

    @Test
    void projeDavetleriListelenirVeIptalEdilir() throws Exception {
        long projectId = createProject("İptal", true);
        long first = inviteOk(projectId, stranger.id());
        AuthenticatedUser second = newUser(Role.USER);
        inviteOk(projectId, second.id());

        perform(get("/projects/" + projectId + "/invitations"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].invitee.id").value(second.id())); // yeni önce
        perform(get("/projects/" + projectId + "/invitations"), member)
                .andExpect(status().isForbidden());

        perform(delete("/projects/" + projectId + "/invitations/" + first), member).andExpect(status().isForbidden());
        perform(delete("/projects/" + projectId + "/invitations/" + first), owner).andExpect(status().isNoContent());
        perform(delete("/projects/" + projectId + "/invitations/" + first), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVITATION_NOT_PENDING"));

        perform(post("/invitations/" + first + "/accept"), stranger).andExpect(status().isConflict());
        perform(get("/projects/" + projectId + "/invitations?status=PENDING"), owner)
                .andExpect(jsonPath("$.length()").value(1));
        perform(get("/projects/" + projectId + "/invitations?status=CANCELLED"), admin)
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].respondedAt").exists());

        // İptalden sonra aynı kişi yeniden davet edilebilir (24 saat kuralı yalnızca red için)
        invite(projectId, owner, stranger.id()).andExpect(status().isCreated());
    }

    @Test
    void baskaProjeninDavetiBuYoldanIptalEdilemez() throws Exception {
        long projectA = createProject("A", false);
        long projectB = createProject("B", false);
        long invitationInA = inviteOk(projectA, stranger.id());

        perform(delete("/projects/" + projectB + "/invitations/" + invitationInA), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void baskasininDavetleriGorunmez() throws Exception {
        long projectId = createProject("Gizlilik", false);
        inviteOk(projectId, stranger.id());

        perform(get("/invitations"), member).andExpect(jsonPath("$.length()").value(0));
        perform(get("/invitations?status=GECERSIZ"), stranger).andExpect(status().isBadRequest());
    }

    private Integer invitationCount(long projectId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM project_invitations WHERE project_id = ?", Integer.class,
                projectId);
    }
}
