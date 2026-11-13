package com.kafein.todo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

/**
 * Gerçek port üzerinde route, CORS ve kimlik filtresi davranışı.
 * Eureka kapalı olduğu için hiçbir servisin örneği yok: route'a eşleşen ve filtreden
 * geçen istek 503 alır (bu, isteğin doğru lb:// hedefine yönlendirildiğini gösterir).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "FRONTEND_ORIGIN=http://localhost:4200"})
class GatewayRoutingIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

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
    void everyServiceRouteExists() {
        for (String path : new String[]{"/api/users/me", "/api/projects", "/api/invitations",
                "/api/tasks", "/api/dashboard"}) {
            client.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer dummy")
                    .exchange()
                    .expectStatus().isEqualTo(503);
        }
    }

    @Test
    void internalPathsAreNotRouted() {
        client.delete().uri("/internal/tasks/by-project/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer dummy")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void encodedTraversalIsRejected() {
        // Ham, tam URI: istemci tarafında normalize/yeniden kodlama olmasın
        // (URI nesnesine base URL eklenmediği için port açıkça yazılır)
        client.delete().uri(URI.create("http://localhost:" + port + "/api/tasks/%2e%2e/internal/tasks/by-project/1"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer dummy")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.error").isEqualTo("BAD_REQUEST");
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
}
