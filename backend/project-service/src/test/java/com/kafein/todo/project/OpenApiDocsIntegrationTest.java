package com.kafein.todo.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class OpenApiDocsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ObjectMapper json;

    /** docs/06'daki uçlar (servis içi yollar; gateway'de başlarına /api gelir). */
    private static final Set<String> EXPECTED = Set.of(
            "POST /projects",
            "GET /projects",
            "GET /projects/{id}",
            "PUT /projects/{id}",
            "DELETE /projects/{id}",
            "GET /projects/{projectId}/members",
            "DELETE /projects/{projectId}/members/{userId}",
            "POST /projects/{projectId}/invitations",
            "GET /projects/{projectId}/invitations",
            "DELETE /projects/{projectId}/invitations/{invitationId}",
            "GET /invitations",
            "POST /invitations/{id}/accept",
            "POST /invitations/{id}/reject");

    /** Token istemeyen uçlar. */
    private static final Set<String> OPEN = Set.of();

    /** Uca özgü, belgede görünmesi gereken hata kodları (docs/06 hata tablolarından). */
    private static final Map<String, Set<String>> ERRORS = Map.of(
            "GET /projects/{id}", Set.of("403 PROJECT_ACCESS_DENIED", "404 PROJECT_NOT_FOUND"),
            "PUT /projects/{id}", Set.of("409 PROJECT_ARCHIVED"),
            "DELETE /projects/{projectId}/members/{userId}", Set.of("409 OWNER_CANNOT_BE_REMOVED"),
            "POST /projects/{projectId}/invitations", Set.of("409 INVITATION_ALREADY_PENDING", "503 USER_SERVICE_UNAVAILABLE"),
            "POST /invitations/{id}/accept", Set.of("403 INVITATION_FORBIDDEN", "409 INVITATION_NOT_PENDING"));

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
        assertThat(doc.at("/components/schemas").has("MembershipResponse")).isFalse();
        assertThat(doc.at("/components/schemas").has("ProjectSummaryResponse")).isFalse();
        assertThat(operations).containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(problems).isEmpty();
    }
}
