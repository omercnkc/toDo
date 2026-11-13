package com.kafein.todo.auth.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.auth.TestcontainersConfiguration;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gerçek güvenlik zinciri + gerçek MySQL ile uçtan uca. Uygulamanın tamamının ayağa kalktığını
 * (Flyway, Hibernate şema doğrulaması, yapılandırma) da doğrular.
 * Testler birbirinden bağımsız olsun diye her test kendi benzersiz e-postasını kullanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Gizli.Sifre1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    // ---------- register ----------

    @Test
    void kayit201VeKullaniciOzetiDoner() throws Exception {
        String email = uniqueEmail();

        mvc.perform(register(email, PASSWORD, "Ali Veli"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Ali Veli"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void ayniEpostaIkinciKez409() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Ali")).andExpect(status().isCreated());

        mvc.perform(register(email.toUpperCase(), PASSWORD, "Ali 2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.path").value("/auth/register"));
    }

    @Test
    void gecersizKayitAlanBazindaHataDoner() throws Exception {
        mvc.perform(register("e-posta-degil", "kisa", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("email", "password", "fullName")));
    }

    @Test
    void istemciRolGonderseBileKayitUserOlur() throws Exception {
        String email = uniqueEmail();
        String body = """
                {"email":"%s","password":"%s","fullName":"Hacker","role":"ADMIN"}""".formatted(email, PASSWORD);

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // ---------- login ----------

    @Test
    void girisAccessTokenVeHttpOnlyRefreshCookieDoner() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Ayşe")).andExpect(status().isCreated());

        MvcResult result = mvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Access token geçerli ve doğru kullanıcıya ait
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        var user = jwtService.parseAccessToken(body.get("accessToken").asText());
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.role()).isEqualTo(Role.USER);

        // Cookie bayrakları docs/07 ile birebir
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");

        // DB'de ham token değil hash'i duruyor
        String rawToken = cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
        Integer rawStored = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, rawToken);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.email = ? AND rt.revoked = FALSE", Integer.class, email);
        assertThat(rawStored).isZero();
        assertThat(active).isEqualTo(1);
    }

    @Test
    void yanlisSifre401() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Can")).andExpect(status().isCreated());

        mvc.perform(login(email, "Yanlis.Sifre1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void olmayanKullanici401AyniMesaj() throws Exception {
        mvc.perform(login(uniqueEmail(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("E-posta veya şifre hatalı"));
    }

    @Test
    void pasifKullanici403() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Ece")).andExpect(status().isCreated());
        jdbc.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        mvc.perform(login(email, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    void bozukJson400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bozuk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // ---------- güvenlik zinciri ----------

    @Test
    void korumaliUcTokensiz401ApiErrorDoner() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/users/me"));
    }

    @Test
    void gecersizToken401() throws Exception {
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer bozuk.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gecerliTokenGuvenliktenGecer() throws Exception {
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Deniz")).andExpect(status().isCreated());
        String token = objectMapper.readTree(mvc.perform(login(email, PASSWORD)).andReturn()
                .getResponse().getContentAsString()).get("accessToken").asText();

        // /users/me henüz yok (adım 2-D): güvenlikten geçip 404 alması token'ın kabul edildiğini gösterir
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void acikUcaBozukTokenlaGelmekEngellemez() throws Exception {
        // Tarayıcıda süresi dolmuş token kalmış olabilir; giriş yine çalışmalı
        String email = uniqueEmail();
        mvc.perform(register(email, PASSWORD, "Ece")).andExpect(status().isCreated());

        mvc.perform(login(email, PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer eski.bozuk.token"))
                .andExpect(status().isOk());
    }

    @Test
    void healthAcik() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---------- yardımcılar ----------

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID().toString().substring(0, 8) + "@ornek.com";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String email, String password, String fullName) throws Exception {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "email", email, "password", password, "fullName", fullName))));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) throws Exception {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password)));
    }
}
