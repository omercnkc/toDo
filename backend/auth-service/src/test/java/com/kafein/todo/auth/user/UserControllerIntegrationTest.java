package com.kafein.todo.auth.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.auth.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kullanıcı uçları: profil, şifre, arama, ADMIN yönetimi, servisler arası uçlar, ADMIN seed.
 * Gerçek güvenlik zinciri ({@code @PreAuthorize} dahil) + gerçek MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserControllerIntegrationTest {

    private static final String PASSWORD = "Gizli.Sifre1";
    private static final String ADMIN_EMAIL = "admin@test.local";   // application-test.yml
    private static final String ADMIN_PASSWORD = "Admin.Sifre1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    // ---------- ADMIN seed ----------

    @Test
    void varsayilanAdminOlusturulmusturVeGirisYapabilir() throws Exception {
        mvc.perform(json(post("/auth/login"), Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    // ---------- profil ----------

    @Test
    void profilGetirilir() throws Exception {
        Session ali = newUser("Ali Profil");

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, ali.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ali.id()))
                .andExpect(jsonPath("$.email").value(ali.email()))
                .andExpect(jsonPath("$.fullName").value("Ali Profil"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void adGuncellenir() throws Exception {
        Session ayse = newUser("Ayşe Eski");

        mvc.perform(json(put("/users/me"), Map.of("fullName", "  Ayşe Yeni  ")).header(HttpHeaders.AUTHORIZATION, ayse.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ayşe Yeni"));
        mvc.perform(json(put("/users/me"), Map.of("fullName", " ")).header(HttpHeaders.AUTHORIZATION, ayse.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"));
    }

    // ---------- şifre ----------

    @Test
    void sifreDegisinceEskiSifreVeTumOturumlarGecersizOlur() throws Exception {
        Session can = newUser("Can Şifre");
        String otherDevice = loginCookie(can.email(), PASSWORD);

        mvc.perform(json(put("/users/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", "Yeni.Sifre2"))
                        .header(HttpHeaders.AUTHORIZATION, can.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(json(post("/auth/login"), Map.of("email", can.email(), "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/login"), Map.of("email", can.email(), "password", "Yeni.Sifre2")))
                .andExpect(status().isOk());
        // Değişiklikten önce açılmış oturumlar yenilenemez (FORCED)
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", can.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", otherDevice)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM refresh_tokens WHERE token_hash = SHA2(?, 256)",
                Integer.class, otherDevice)).isEqualTo(3);
    }

    @Test
    void mevcutSifreYanlissaAlanHatasi() throws Exception {
        Session deniz = newUser("Deniz");

        mvc.perform(json(put("/users/me/password"), Map.of("currentPassword", "Yanlis.Sifre", "newPassword", "Yeni.Sifre2"))
                        .header(HttpHeaders.AUTHORIZATION, deniz.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
    }

    @Test
    void yeniSifreEskisiyleAyniOlamaz() throws Exception {
        Session ece = newUser("Ece");

        mvc.perform(json(put("/users/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))
                        .header(HttpHeaders.AUTHORIZATION, ece.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAME_PASSWORD"));
    }

    @Test
    void yeniSifrePolitikayaUymali() throws Exception {
        Session fatma = newUser("Fatma");

        mvc.perform(json(put("/users/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", "kisa"))
                        .header(HttpHeaders.AUTHORIZATION, fatma.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    // ---------- arama ----------

    @Test
    void userAramasiYalnizcaAktifleriVeOzetiDoner() throws Exception {
        String tag = "Ara" + UUID.randomUUID().toString().substring(0, 6);
        Session searcher = newUser(tag + " Arayan");
        Session passive = newUser(tag + " Pasif");
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", passive.id());

        mvc.perform(get("/users").param("q", tag.toLowerCase()).header(HttpHeaders.AUTHORIZATION, searcher.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(searcher.id()))
                .andExpect(jsonPath("$.content[0].fullName").value(tag + " Arayan"))
                .andExpect(jsonPath("$.content[0].role").doesNotExist())
                .andExpect(jsonPath("$.content[0].enabled").doesNotExist());
    }

    @Test
    void userAramasindaEnAzUcKarakter() throws Exception {
        Session user = newUser("Kısa Arama");

        mvc.perform(get("/users").param("q", "ab").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jokerKarakterlerTumKullanicilariDondurmez() throws Exception {
        Session user = newUser("Joker");

        mvc.perform(get("/users").param("q", "%%%").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/users").param("q", "___").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void adminAramasiPasifleriDeVeDetayiDoner() throws Exception {
        String tag = "Yon" + UUID.randomUUID().toString().substring(0, 6);
        Session passive = newUser(tag + " Pasif");
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", passive.id());

        mvc.perform(get("/users").param("q", tag).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(passive.id()))
                .andExpect(jsonPath("$.content[0].enabled").value(false))
                .andExpect(jsonPath("$.content[0].role").value("USER"));
        // q olmadan tüm liste (ADMIN kullanıcı yönetimi ekranı)
        mvc.perform(get("/users").param("size", "100").header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email").value(hasItem(ADMIN_EMAIL)));
    }

    @Test
    void sayfaBoyutuSiniri() throws Exception {
        mvc.perform(get("/users").param("size", "500").header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    // ---------- ADMIN uçları ----------

    @Test
    void userAdminUcunaErisemez() throws Exception {
        Session user = newUser("Meraklı");

        mvc.perform(get("/users/" + user.id()).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(json(put("/users/" + user.id()), Map.of("role", "ADMIN")).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDetayGorur() throws Exception {
        Session user = newUser("Detay");

        mvc.perform(get("/users/" + user.id()).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));
        mvc.perform(get("/users/999999999").header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void pasiflestirilenKullanicininOturumlariKapanir() throws Exception {
        Session user = newUser("Pasifleşecek");

        mvc.perform(json(put("/users/" + user.id()), Map.of("enabled", false)).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", user.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/auth/login"), Map.of("email", user.email(), "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    void rolDegisikligiSonrakiTokendaGorunur() throws Exception {
        Session user = newUser("Terfi");

        mvc.perform(json(put("/users/" + user.id()), Map.of("role", "ADMIN")).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // Yenileme yeni rolü taşıyan access token verir
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", user.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void adminKendiniDegistiremez() throws Exception {
        Session admin = admin();

        mvc.perform(json(put("/users/" + admin.id()), Map.of("enabled", false)).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_MODIFICATION_NOT_ALLOWED"));
    }

    @Test
    void bosGuncelleme400() throws Exception {
        Session user = newUser("Boş");

        mvc.perform(json(put("/users/" + user.id()), Map.of()).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/users/" + user.id()), Map.of("role", "ROOT")).header(HttpHeaders.AUTHORIZATION, admin().bearer()))
                .andExpect(status().isBadRequest());
    }

    // ---------- servisler arası ----------

    @Test
    void dahiliUclarHerGecerliTokenlaCalisir() throws Exception {
        Session caller = newUser("Servis Çağıran");
        Session target = newUser("Hedef");

        mvc.perform(get("/internal/users/" + target.id()).header(HttpHeaders.AUTHORIZATION, caller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Hedef"))
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(json(post("/internal/users/lookup"), Map.of("ids", new long[]{target.id(), caller.id(), 999999999L}))
                        .header(HttpHeaders.AUTHORIZATION, caller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(everyItem(not(999999999))));
    }

    @Test
    void dahiliUclarTokenIster() throws Exception {
        mvc.perform(get("/internal/users/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void lookupDogrulamasi() throws Exception {
        Session caller = newUser("Lookup");

        mvc.perform(json(post("/internal/users/lookup"), Map.of("ids", new long[0])).header(HttpHeaders.AUTHORIZATION, caller.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ids"));
    }

    @Test
    void kayittaEpostaCakismasiAlanHatasiIcerir() throws Exception {
        Session user = newUser("Çakışma");

        mvc.perform(json(post("/auth/register"), Map.of("email", user.email(), "password", PASSWORD, "fullName", "X")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    // ---------- yardımcılar ----------

    record Session(long id, String email, String accessToken, String refreshToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Session newUser(String fullName) throws Exception {
        String email = "k-" + UUID.randomUUID().toString().substring(0, 8) + "@ornek.com";
        mvc.perform(json(post("/auth/register"), Map.of("email", email, "password", PASSWORD, "fullName", fullName)))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    private Session admin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(json(post("/auth/login"), Map.of("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(body.at("/user/id").asLong(), email, body.get("accessToken").asText(), cookie(result));
    }

    private String loginCookie(String email, String password) throws Exception {
        return login(email, password).refreshToken();
    }

    private static String cookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }
}
