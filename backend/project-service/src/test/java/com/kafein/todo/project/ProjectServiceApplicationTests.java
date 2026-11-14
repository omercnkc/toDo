package com.kafein.todo.project;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uygulama açılır (Flyway migration'ları + Hibernate şema doğrulaması) ve güvenlik zinciri çalışır:
 * health açık, diğer her şey geçerli JWT ister. Hatalar ortak ApiError biçiminde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectServiceApplicationTests {

    /** application-test.yml ile aynı; auth-service'in ürettiği token'ı taklit eder. */
    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @Autowired
    private MockMvc mvc;

    private static String token(String secret, Clock clock) {
        return new JwtService(secret, Duration.ofMinutes(15), clock)
                .generateAccessToken(new AuthenticatedUser(5L, "ali@ornek.com", Role.USER));
    }

    @Test
    void healthGirisGerektirmez() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tokenYoksa401() throws Exception {
        mvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void baskaAnahtarlaImzalanmisVeyaSuresiDolmusToken401() throws Exception {
        String foreign = token("baska-bir-anahtar-baska-bir-anahtar-123", Clock.systemUTC());
        String expired = token(SECRET, Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));

        for (String bad : new String[]{foreign, expired, "bozuk.token.degeri"}) {
            mvc.perform(get("/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Test
    void gecerliTokenGuvenlikZincirindenGecer() throws Exception {
        mvc.perform(get("/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, Clock.systemUTC())))
                .andExpect(status().isOk());
        // Olmayan yol: 401 değil, ortak biçimde 404
        mvc.perform(get("/olmayan-yol").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, Clock.systemUTC())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
