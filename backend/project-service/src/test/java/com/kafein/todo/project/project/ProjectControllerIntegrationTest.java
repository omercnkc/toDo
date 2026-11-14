package com.kafein.todo.project.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.IntegrationTestSupport;
import com.kafein.todo.project.member.ProjectMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proje uçları: gerçek güvenlik zinciri + gerçek MySQL (ortak kurulum {@link IntegrationTestSupport}). */
class ProjectControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private List<Long> listIds(AuthenticatedUser as, String query) throws Exception {
        String json = perform(get("/projects" + query), as).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(json).get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    // ---------- oluşturma ----------

    @Test
    void olusturanOwnerOlurVeYanitTamGelir() throws Exception {
        String json = send(post("/projects"), owner, Map.of("name", "  Web Sitesi  ", "description", "  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Web Sitesi"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerId").value(owner.id()))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(json).get("id").asLong();
        ProjectMember ownership = members.findByProjectIdAndUserId(id, owner.id()).orElseThrow();
        assertThat(ownership.isOwner()).isTrue();
    }

    @Test
    void gecersizOlusturmaIstegi400AlanHatalariyla() throws Exception {
        send(post("/projects"), owner, Map.of("name", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        send(post("/projects"), owner, Map.of("name", "x".repeat(151), "description", "y".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("description")));
    }

    // ---------- liste ----------

    @Test
    void userYalnizcaUyesiOlduguProjeleriGorurAdminHepsini() throws Exception {
        long shared = createProject("Ortak", true);
        long ownOnly = createProject("Sadece sahibin", false);

        assertThat(listIds(member, "")).containsExactly(shared);
        assertThat(listIds(owner, "")).containsExactly(ownOnly, shared); // son oluşturulan önce
        assertThat(listIds(stranger, "")).isEmpty();
        assertThat(listIds(admin, "?size=100")).contains(shared, ownOnly);

        perform(get("/projects"), member)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$.content[0].canManage").value(false))
                .andExpect(jsonPath("$.content[0].memberCount").value(2));
    }

    @Test
    void adminUyeOlmadigiProjedeRolsuzAmaYonetebilir() throws Exception {
        long id = createProject("Admin görür", false);

        String json = perform(get("/projects?size=100"), admin).andReturn().getResponse().getContentAsString();
        JsonNode node = findById(objectMapper.readTree(json).get("content"), id);
        assertThat(node.get("myRole").isNull()).isTrue();
        assertThat(node.get("canManage").asBoolean()).isTrue();
    }

    @Test
    void aramaVeDurumFiltresi() throws Exception {
        long alpha = createProject("Alfa %100 proje", false);
        long beta = createProject("Beta_projesi", false);
        long archived = createProject("Alfa arşiv", false);
        perform(delete("/projects/" + archived), owner).andExpect(status().isNoContent());

        assertThat(listIds(owner, "?q=alfa")).containsExactlyInAnyOrder(alpha, archived);
        // URL şablonu % işaretini yeniden kodlayacağı için parametre ayrı verilir
        String json = perform(get("/projects").param("q", "%"), owner).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).get("content")).as("%% joker değil, düz karakter")
                .extracting(node -> node.get("id").asLong()).containsExactly(alpha);
        assertThat(listIds(owner, "?q=Alf_")).as("_ joker değil").isEmpty();
        assertThat(listIds(owner, "?q=_")).containsExactly(beta);
        assertThat(listIds(owner, "?status=ARCHIVED")).containsExactly(archived);
        assertThat(listIds(owner, "?status=ACTIVE")).containsExactlyInAnyOrder(alpha, beta);
    }

    @Test
    void sayfalamaVeGecersizParametreler() throws Exception {
        createProject("P1", false);
        createProject("P2", false);
        createProject("P3", false);

        perform(get("/projects?size=2&page=1"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1));

        perform(get("/projects?size=101"), owner).andExpect(status().isBadRequest());
        perform(get("/projects?page=-1"), owner).andExpect(status().isBadRequest());
        perform(get("/projects?status=SILINDI"), owner).andExpect(status().isBadRequest());
    }

    // ---------- detay ----------

    @Test
    void detayUyeyeAcikYabanciya403OlmayanProjeye404() throws Exception {
        long id = createProject("Detay", true);

        perform(get("/projects/" + id), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Detay"))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.memberCount").value(2));
        perform(get("/projects/" + id), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").doesNotExist())
                .andExpect(jsonPath("$.canManage").value(true));
        perform(get("/projects/" + id), stranger)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        perform(get("/projects/999999999"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"));
    }

    // ---------- düzenleme ----------

    @Test
    void duzenlemeYalnizcaOwnerVeAdmin() throws Exception {
        long id = createProject("Eski ad", true);

        send(put("/projects/" + id), member, Map.of("name", "Üye değiştirdi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_MANAGE_DENIED"));
        send(put("/projects/" + id), stranger, Map.of("name", "Yabancı"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));

        send(put("/projects/" + id), owner, Map.of("name", " Yeni ad ", "description", "Yeni açıklama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yeni ad"))
                .andExpect(jsonPath("$.description").value("Yeni açıklama"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        send(put("/projects/" + id), admin, Map.of("name", "Admin düzenledi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());

        send(put("/projects/" + id), owner, Map.of("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    // ---------- arşiv ----------

    @Test
    void arsivlemeVeArsivdenGeriAlma() throws Exception {
        long id = createProject("Arşivlenecek", true);

        perform(delete("/projects/" + id), member).andExpect(status().isForbidden());
        perform(delete("/projects/" + id), owner).andExpect(status().isNoContent());
        perform(delete("/projects/" + id), owner).andExpect(status().isNoContent()); // idempotent
        assertThat(projects.findById(id).orElseThrow().getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

        // Arşivdeyken görülebilir ama düzenlenemez
        perform(get("/projects/" + id), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        send(put("/projects/" + id), owner, Map.of("name", "Değişmez"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));

        // Aynı istekte status=ACTIVE ile geri alınır
        send(put("/projects/" + id), owner, Map.of("name", "Geri geldi", "status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value("Geri geldi"));

        // PUT ile de arşivlenebilir
        send(put("/projects/" + id), admin, Map.of("name", "Geri geldi", "status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void kaliciSilmeHenuzYok() throws Exception {
        long id = createProject("Kalıcı", false);

        perform(delete("/projects/" + id + "?permanent=true"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PERMANENT_DELETE_NOT_AVAILABLE"));
        assertThat(projects.existsById(id)).isTrue();
    }

    @Test
    void yetkisizArsivProjeyiDegistirmez() throws Exception {
        long id = createProject("Korunan", true);

        perform(delete("/projects/" + id), stranger).andExpect(status().isForbidden());
        perform(delete("/projects/" + id), member).andExpect(status().isForbidden());

        Integer status = jdbc.queryForObject("SELECT status FROM projects WHERE id = ?", Integer.class, id);
        assertThat(status).isZero();
        assertThat(listIds(stranger, "")).doesNotContain(id);
        perform(get("/projects"), stranger).andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
    }

    private static JsonNode findById(JsonNode content, long id) {
        for (JsonNode node : content) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("Listede yok: " + id);
    }
}
