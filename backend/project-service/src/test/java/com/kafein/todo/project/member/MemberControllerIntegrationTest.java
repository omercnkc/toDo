package com.kafein.todo.project.member;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.project.IntegrationTestSupport;
import com.kafein.todo.project.client.UserSummary;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Üye uçları ve servisler arası (dahili) uçlar. auth-service mock (bkz. {@link IntegrationTestSupport}). */
class MemberControllerIntegrationTest extends IntegrationTestSupport {

    private static FeignException serviceUnavailable() {
        Request request = Request.create(Request.HttpMethod.POST, "http://auth-service/internal/users/lookup",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("auth-service kapalı", request, null, Map.of());
    }

    // ---------- liste ----------

    @Test
    void uyeListesiOwnerOnceAdlarAuthServistenGelir() throws Exception {
        long id = createProject("Üyeli", true);

        perform(get("/projects/" + id + "/members"), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(owner.id()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].fullName").value("Kullanıcı " + owner.id()))
                .andExpect(jsonPath("$[0].email").value("kullanici" + owner.id() + "@ornek.com"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].joinedAt").exists())
                .andExpect(jsonPath("$[1].userId").value(member.id()))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));

        // Tek toplu çağrı, iki id
        ArgumentCaptor<com.kafein.todo.project.client.AuthClient.LookupRequest> captor =
                ArgumentCaptor.forClass(com.kafein.todo.project.client.AuthClient.LookupRequest.class);
        verify(authClient).lookup(captor.capture());
        assertThat(captor.getValue().ids()).containsExactlyInAnyOrder(owner.id(), member.id());
    }

    @Test
    void pasifKullaniciListedeIsaretlenirBulunamayaninAdiBosKalir() throws Exception {
        long id = createProject("Pasifli", true);
        // doReturn: when(...) yeniden stub'larken önceki cevabı null argümanla çağırırdı
        doReturn(List.of(
                new UserSummary(owner.id(), "sahip@ornek.com", "Sahip", true),
                new UserSummary(member.id(), "pasif@ornek.com", "Pasif Üye", false)))
                .when(authClient).lookup(any());
        AuthenticatedUser ghost = newUser(Role.USER);
        addMember(id, ghost.id());

        perform(get("/projects/" + id + "/members"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].enabled").value(false))
                .andExpect(jsonPath("$[2].userId").value(ghost.id()))
                .andExpect(jsonPath("$[2].fullName").doesNotExist())
                .andExpect(jsonPath("$[2].enabled").doesNotExist());
    }

    @Test
    void authServisKapaliysaListeAdsizDoner() throws Exception {
        long id = createProject("Kesinti", true);
        doThrow(serviceUnavailable()).when(authClient).lookup(any());

        perform(get("/projects/" + id + "/members"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].fullName").doesNotExist());
    }

    @Test
    void uyeListesiYetkisi() throws Exception {
        long id = createProject("Gizli üyeler", false);

        perform(get("/projects/" + id + "/members"), stranger)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        perform(get("/projects/" + id + "/members"), admin).andExpect(status().isOk());
        perform(get("/projects/999999999/members"), owner).andExpect(status().isNotFound());
    }

    // ---------- çıkarma ----------

    @Test
    void ownerUyeyiCikarirCikarilanArtikProjeyiGoremez() throws Exception {
        long id = createProject("Çıkarma", true);

        perform(delete("/projects/" + id + "/members/" + member.id()), owner).andExpect(status().isNoContent());

        assertThat(members.existsByProjectIdAndUserId(id, member.id())).isFalse();
        perform(get("/projects/" + id), member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        perform(get("/projects/" + id), owner).andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void cikarmaKurallari() throws Exception {
        long id = createProject("Kurallar", true);
        AuthenticatedUser other = newUser(Role.USER);
        addMember(id, other.id());

        perform(delete("/projects/" + id + "/members/" + other.id()), member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_MANAGE_DENIED"));
        perform(delete("/projects/" + id + "/members/" + owner.id()), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OWNER_CANNOT_BE_REMOVED"));
        perform(delete("/projects/" + id + "/members/" + stranger.id()), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEMBER_NOT_FOUND"));

        perform(delete("/projects/" + id + "/members/" + other.id()), admin).andExpect(status().isNoContent());
    }

    @Test
    void arsivlenmisProjedeUyeCikarilamaz() throws Exception {
        long id = createProject("Arşivli üyelik", true);
        perform(delete("/projects/" + id), owner).andExpect(status().isNoContent());

        perform(delete("/projects/" + id + "/members/" + member.id()), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));

        // Arşivden çıkınca çıkarılabilir
        send(put("/projects/" + id), owner, Map.of("name", "Arşivli üyelik", "status", "ACTIVE"))
                .andExpect(status().isOk());
        perform(delete("/projects/" + id + "/members/" + member.id()), owner).andExpect(status().isNoContent());
    }

    // ---------- dahili uçlar ----------

    @Test
    void dahiliUyelikKontrolu() throws Exception {
        long id = createProject("Dahili", true);

        perform(get("/internal/projects/" + id + "/members/" + member.id()), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(id))
                .andExpect(jsonPath("$.userId").value(member.id()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.projectStatus").value("ACTIVE"));
        perform(get("/internal/projects/" + id + "/members/" + stranger.id()), member)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEMBERSHIP_NOT_FOUND"));
        perform(get("/internal/projects/999999999/members/" + member.id()), member)
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/projects/" + id + "/members/" + member.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dahiliErisilebilirProjeler() throws Exception {
        long first = createProject("Erişilebilir 1", true);
        long second = createProject("Erişilebilir 2", true);
        createProject("Erişilemez", false);

        String json = perform(get("/internal/projects/accessible").param("userId", String.valueOf(member.id())), member)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(json, Long[].class)).containsExactly(first, second);

        perform(get("/internal/projects/accessible").param("userId", String.valueOf(stranger.id())), stranger)
                .andExpect(jsonPath("$.length()").value(0));
    }
}
