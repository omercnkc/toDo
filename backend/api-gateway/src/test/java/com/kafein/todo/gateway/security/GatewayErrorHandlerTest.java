package com.kafein.todo.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final GatewayErrorHandler handler = new GatewayErrorHandler(new ErrorResponseWriter(objectMapper));

    private JsonNode handle(Throwable ex, HttpStatus expected) throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/tasks"));
        handler.handle(exchange, ex).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
        String body = exchange.getResponse().getBodyAsString().block();
        return objectMapper.readTree(body);
    }

    @Test
    void ulasilamayanServis503() throws Exception {
        // Docker'da durdurulmuş konteyner: adı çözülmez; IDE'de kapalı süreç: bağlantı reddi; Eureka'da örnek yok: 503
        Throwable[] unreachable = {
                new RuntimeException(new UnknownHostException("Failed to resolve 'task-service'")),
                new RuntimeException("wrap", new ConnectException("Connection refused")),
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance")};
        for (Throwable ex : unreachable) {
            JsonNode body = handle(ex, HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(body.get("error").asText()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(body.get("path").asText()).isEqualTo("/api/tasks");
        }
    }

    @Test
    void zamanAsimi504DigerleriKendiDurumuBeklenmeyen500DetaySizdirmaz() throws Exception {
        assertThat(handle(new RuntimeException(new TimeoutException()), HttpStatus.GATEWAY_TIMEOUT)
                .get("error").asText()).isEqualTo("GATEWAY_TIMEOUT");
        assertThat(handle(new ResponseStatusException(HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND)
                .get("error").asText()).isEqualTo("NOT_FOUND");
        JsonNode body = handle(new IllegalStateException("gizli iç ayrıntı"), HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.get("error").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.toString()).doesNotContain("gizli");
    }
}
