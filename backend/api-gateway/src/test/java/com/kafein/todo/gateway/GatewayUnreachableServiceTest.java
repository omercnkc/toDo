package com.kafein.todo.gateway;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

/**
 * Servis kaydı var ama süreç kapalı (ör. az önce durdu, Eureka kaydı henüz düşmedi): bağlantı reddedilir.
 * Spring'in varsayılan işleyicisi bunu 500 yapıyordu; gateway 503 {@code SERVICE_UNAVAILABLE} döner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayUnreachableServiceTest {

    private static final JwtService ISSUER =
            new JwtService("test-secret-test-secret-test-secret-123", Duration.ofMinutes(15));

    @DynamicPropertySource
    static void taskServiceOnClosedPort(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[0].uri",
                () -> "http://127.0.0.1:" + closedPort);
    }

    @Autowired
    private WebTestClient client;

    @Test
    void connectionRefusedIs503ApiError() {
        String token = "Bearer " + ISSUER.generateAccessToken(new AuthenticatedUser(1L, "a@b.com", Role.USER));
        client.get().uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo("/api/tasks");
    }
}
