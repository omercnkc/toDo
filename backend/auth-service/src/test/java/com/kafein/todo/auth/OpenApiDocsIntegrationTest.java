package com.kafein.todo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI belgesi (7-B): token'sız okunur, uç listesi docs/06 ile birebir, {@code /internal/**} yok,
 * Bearer şeması, hata yanıtları {@code ApiError}. İncelemek için {@code target/openapi.json}'a yazılır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    /** docs/06'daki uçlar (servis içi yollar; gateway'de başlarına /api gelir). */
    private static final Set<String> EXPECTED = Set.of(
            "POST /auth/register",
            "POST /auth/login",
            "POST /auth/refresh",
            "POST /auth/logout",
            "GET /users/me",
            "PUT /users/me",
            "PUT /users/me/password",
            "GET /users",
            "GET /users/{id}",
            "PUT /users/{id}");

    /** Token istemeyen uçlar. */
    private static final Set<String> OPEN = Set.of("POST /auth/register", "POST /auth/login", "POST /auth/refresh", "POST /auth/logout");

    /** Uca özgü, belgede görünmesi gereken hata kodları (docs/06 hata tablolarından). */
    private static final Map<String, Set<String>> ERRORS = Map.of(
            "POST /auth/register", Set.of("409 EMAIL_ALREADY_EXISTS"),
            "POST /auth/login", Set.of("401 INVALID_CREDENTIALS", "403 ACCOUNT_DISABLED"),
            "POST /auth/refresh", Set.of("401 INVALID_REFRESH_TOKEN"),
            "PUT /users/me/password", Set.of("400 INVALID_CURRENT_PASSWORD"),
            "GET /users/{id}", Set.of("403 FORBIDDEN", "404 USER_NOT_FOUND"),
            "PUT /users/{id}", Set.of("409 SELF_MODIFICATION_NOT_ALLOWED"));

    @Test
    void apiDocsArePublicAndMatchContract() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode doc = json.readTree(body);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(doc));

        assertThat(doc.at("/servers/0/url").asText()).isEqualTo("/api");
        assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(doc.at("/components/schemas/ApiError/properties/error").isMissingNode()).isFalse();

        Set<String> operations = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        doc.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            String key = op.getKey().toUpperCase() + " " + path.getKey();
            operations.add(key);
            JsonNode operation = op.getValue();
            if (operation.path("summary").asText().isBlank()) {
                problems.add(key + ": summary yok");
            }
            operation.path("parameters").forEach(p -> {
                String name = p.path("name").asText();
                if (name.equals("user") || name.equals("admin")) {
                    problems.add(key + ": @AuthenticationPrincipal parametre olarak görünüyor");
                }
            });
            boolean open = operation.has("security") && operation.get("security").isEmpty();
            if (open != OPEN.contains(key)) {
                problems.add(key + ": güvenlik beklenen gibi değil (açık=" + open + ")");
            }
            JsonNode responses = operation.path("responses");
            if (!open && !responses.has("401")) {
                problems.add(key + ": 401 yok");
            }
            responses.fields().forEachRemaining(r -> {
                boolean error = r.getKey().startsWith("4") || r.getKey().startsWith("5");
                if (error && !r.getValue().at("/content/application~1json/schema/$ref").asText().endsWith("/ApiError")) {
                    problems.add(key + " " + r.getKey() + ": ApiError şeması yok");
                }
            });
            if (!responses.fieldNames().hasNext() || !responses.fieldNames().next().startsWith("2")) {
                problems.add(key + ": başarılı yanıt kodu yok");
            }
            ERRORS.getOrDefault(key, Set.of()).forEach(code -> {
                String[] parts = code.split(" ", 2);
                if (!responses.path(parts[0]).path("description").asText().contains(parts[1])) {
                    problems.add(key + ": " + code + " belgede yok");
                }
            });
        }));

        assertThat(operations).noneMatch(op -> op.contains("/internal"));
        // Yalnızca /internal uçlarının DTO'ları belgeye sızmamalı
        assertThat(doc.at("/components/schemas").has("InternalUserResponse")).isFalse();
        assertThat(doc.at("/components/schemas").has("LookupRequest")).isFalse();
        assertThat(operations).containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(problems).isEmpty();
    }
}
