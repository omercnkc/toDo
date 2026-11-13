package com.kafein.todo.auth.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.auth.TestcontainersConfiguration;
import com.kafein.todo.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh rotasyonu, tekrar kullanım tespiti ve logout; gerçek MySQL üzerinde.
 * Context {@link AuthControllerIntegrationTest} ile aynı (Spring önbelleği, tek MySQL konteyneri).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefreshFlowIntegrationTest {

    private static final String PASSWORD = "Gizli.Sifre1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private String email;

    @BeforeEach
    void registerUser() throws Exception {
        email = "r-" + UUID.randomUUID().toString().substring(0, 8) + "@ornek.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD, "fullName", "Yenileme Testi"))))
                .andExpect(status().isCreated());
    }

    // ---------- refresh ----------

    @Test
    void yenilemeYeniAccessTokenVeYeniCookieVerirEskisiniIptalEder() throws Exception {
        String first = login();

        MvcResult result = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        String second = cookieValue(result);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(jwtService.parseAccessToken(accessToken).email()).isEqualTo(email);
        assertThat(revokeReason(first)).isEqualTo(0);   // ROTATED
        assertThat(revokeReason(second)).isNull();      // aktif
    }

    @Test
    void zincirlemeYenilemeCalisir() throws Exception {
        String token = login();
        for (int i = 0; i < 3; i++) {
            token = cookieValue(refresh(token).andExpect(status().isOk()).andReturn());
        }
        assertThat(activeTokenCount()).isEqualTo(1);
    }

    @Test
    void cookieYoksa401VeCookieSilinir() throws Exception {
        MvcResult result = mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertCookieCleared(result);
    }

    @Test
    void bilinmeyenToken401() throws Exception {
        assertCookieCleared(refresh("bilinmeyen-token-degeri").andExpect(status().isUnauthorized()).andReturn());
    }

    @Test
    void suresiDolmusToken401() throws Exception {
        String token = login();
        jdbc.update("UPDATE refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                + "SET rt.expires_at = NOW(6) - INTERVAL 1 MINUTE WHERE u.email = ?", email);

        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void pasifKullaniciYenileyemez() throws Exception {
        String token = login();
        jdbc.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        refresh(token).andExpect(status().isUnauthorized());
    }

    // ---------- tekrar kullanım ----------

    @Test
    void eskiTokenToleranstanSonraTekrarKullanilirsaTumOturumlarIptalEdilir() throws Exception {
        String stolen = login();
        String legit = cookieValue(refresh(stolen).andExpect(status().isOk()).andReturn());
        String otherDevice = login(); // aynı kullanıcının başka cihazdaki oturumu
        // Rotasyonun 1 dakika önce olduğunu varsay (tolerans 10 sn)
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW(6) - INTERVAL 1 MINUTE WHERE token_hash = SHA2(?, 256)",
                stolen);

        refresh(stolen).andExpect(status().isUnauthorized());

        // Saldırgan da meşru kullanıcı da dışarıda: tüm aktif token'lar REUSE_DETECTED ile iptal
        assertThat(activeTokenCount()).isZero();
        assertThat(revokeReason(legit)).isEqualTo(2);
        assertThat(revokeReason(otherDevice)).isEqualTo(2);
        refresh(legit).andExpect(status().isUnauthorized());
    }

    @Test
    void ayniAndaIkiYenilemeOturumuKapatmaz() throws Exception {
        // İki sekme aynı cookie ile neredeyse aynı anda yeniler: ikincisi tolerans içinde
        String shared = login();
        String tab1 = cookieValue(refresh(shared).andExpect(status().isOk()).andReturn());
        String tab2 = cookieValue(refresh(shared).andExpect(status().isOk()).andReturn());

        assertThat(tab1).isNotEqualTo(tab2);
        refresh(tab1).andExpect(status().isOk());
        refresh(tab2).andExpect(status().isOk());
    }

    @Test
    void gercektenEsZamanliYenilemelerKilitlenmezVeHepsiBasarili() throws Exception {
        // FOR UPDATE kilidi altında 4 paralel istek: deadlock/500 yok, hepsi 200 alır
        String shared = login();
        int parallel = 4;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return refresh(shared).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(revokeReason(shared)).isEqualTo(0); // ROTATED, REUSE_DETECTED değil
        assertThat(activeTokenCount()).isEqualTo(parallel);
    }

    // ---------- logout ----------

    @Test
    void cikisTokeniIptalEderVeCookieyiSiler() throws Exception {
        String token = login();

        MvcResult result = mvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertCookieCleared(result);
        assertThat(revokeReason(token)).isEqualTo(1); // LOGOUT
        // Çıkıştan hemen sonra bile tolerans uygulanmaz
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void cookiesizCikis204() throws Exception {
        assertCookieCleared(mvc.perform(post("/auth/logout")).andExpect(status().isNoContent()).andReturn());
    }

    @Test
    void ikinciKezCikisDa204() throws Exception {
        String token = login();
        mvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", token))).andExpect(status().isNoContent());
        mvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", token))).andExpect(status().isNoContent());
    }

    @Test
    void cikisVeYenilemeAccessTokenIstemez() throws Exception {
        // Access token süresi dolmuş olsa da bu uçlar çalışmalı (yalnızca cookie)
        String token = login();
        refresh(token).andExpect(status().isOk());
    }

    // ---------- yardımcılar ----------

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return cookieValue(result);
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", token)));
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith("refresh_token=");
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }

    private static void assertCookieCleared(MvcResult result) {
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Max-Age=0", "Path=/api/auth", "HttpOnly");
    }

    private Integer revokeReason(String rawToken) {
        return jdbc.queryForObject("SELECT revoke_reason FROM refresh_tokens WHERE token_hash = SHA2(?, 256)",
                Integer.class, rawToken);
    }

    private int activeTokenCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                + "WHERE u.email = ? AND rt.revoked = FALSE", Integer.class, email);
    }
}
