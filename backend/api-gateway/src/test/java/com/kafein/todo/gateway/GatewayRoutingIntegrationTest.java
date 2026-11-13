package com.kafein.todo.gateway;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.time.Duration;

/**
 * Gerçek port üzerinde route, CORS ve kimlik filtresi davranışı.
 * Eureka kapalı olduğu için hiçbir servisin örneği yok: route'a eşleşen ve filtreden
 * geçen istek 503 alır (bu, isteğin doğru lb:// hedefine yönlendirildiğini gösterir).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "FRONTEND_ORIGIN=http://localhost:4200")
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    /** application-test.yml'deki secret ile aynı (auth-service'in token'ını taklit eder). */
    private static final JwtService ISSUER =
            new JwtService("test-secret-test-secret-test-secret-123", Duration.ofMinutes(15));

    @Autowired
    private WebTestClient client;

    @LocalServerPort
    private int port;

    @Test
    void openPathIsRoutedWithoutToken() {
        client.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void protectedPathWithoutTokenIs401() {
        client.get().uri("/api/projects")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.path").isEqualTo("/api/projects");
    }

    @Test
    void protectedPathWithInvalidTokenIs401() {
        client.get().uri("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer dummy")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void everyServiceRouteExists() {
        String token = validToken();
        for (String path : new String[]{"/api/users/me", "/api/projects", "/api/invitations",
                "/api/tasks", "/api/dashboard"}) {
            client.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .exchange()
                    .expectStatus().isEqualTo(503);
        }
    }

    @Test
    void internalPathsAreNotRouted() {
        client.delete().uri("/internal/tasks/by-project/1")
                .header(HttpHeaders.AUTHORIZATION, validToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void encodedTraversalIsRejected() {
        // Ham, tam URI: istemci tarafında normalize/yeniden kodlama olmasın
        // (URI nesnesine base URL eklenmediği için port açıkça yazılır)
        client.delete().uri(URI.create("http://localhost:" + port + "/api/tasks/%2e%2e/internal/tasks/by-project/1"))
                .header(HttpHeaders.AUTHORIZATION, validToken())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.error").isEqualTo("BAD_REQUEST");
    }

    @Test
    void refreshFromForeignOriginIsRejected() {
        // Uçtan uca ilk engel CORS katmanı (gövdesiz 403); AuthenticationFilter'daki Origin kontrolü
        // CORS gevşetilirse devreye giren ikinci katman (AuthenticationFilterTest'te ayrıca test edilir).
        client.post().uri("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void refreshFromAllowedOriginIsRouted() {
        client.post().uri("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void preflightFromAllowedOriginSucceeds() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    void preflightFromOtherOriginIsRejected() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void corsHeaderAppearsOnceOnActualRequest() {
        client.get().uri("/api/projects")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    private static String validToken() {
        return "Bearer " + ISSUER.generateAccessToken(new AuthenticatedUser(1L, "a@b.com", Role.USER));
    }
}
