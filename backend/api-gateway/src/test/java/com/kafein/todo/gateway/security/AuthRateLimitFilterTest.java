package com.kafein.todo.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new AuthRateLimitProperties(List.of("/api/auth/login", "/api/auth/register"), 3, Duration.ofMinutes(1)),
            new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()), clock);

    @Test
    void fourthLoginFromSameIpInWindowIs429WithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(run(login("10.0.0.1")).passed).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));
        Result blocked = run(login("10.0.0.1"));

        assertThat(blocked.passed).isFalse();
        assertThat(blocked.exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("40");
        assertThat(blocked.exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(blocked.exchange.getResponse().getBodyAsString().block())
                .contains("\"error\":\"TOO_MANY_REQUESTS\"", "\"status\":429");

        // Pencere bitince yeniden izin verilir
        clock.advance(Duration.ofSeconds(40));
        assertThat(run(login("10.0.0.1")).passed).isTrue();
    }

    @Test
    void limitIsPerIpAndPerPath() {
        for (int i = 0; i < 3; i++) {
            run(login("10.0.0.1"));
        }
        assertThat(run(login("10.0.0.1")).passed).isFalse();
        assertThat(run(login("10.0.0.2")).passed).isTrue();
        assertThat(run(post("/api/auth/register", "10.0.0.1")).passed).isTrue();
    }

    @Test
    void otherPathsAndMethodsAreNotLimited() {
        for (int i = 0; i < 10; i++) {
            assertThat(run(post("/api/auth/refresh", "10.0.0.1")).passed).isTrue();
            assertThat(run(MockServerHttpRequest.get("/api/auth/login").header("X-Real-IP", "10.0.0.1").build()).passed)
                    .isTrue();
        }
    }

    @Test
    void withoutRealIpHeaderRemoteAddressIsUsed() {
        for (int i = 0; i < 3; i++) {
            run(fromRemote("192.168.1.5"));
        }
        assertThat(run(fromRemote("192.168.1.5")).passed).isFalse();
        assertThat(run(fromRemote("192.168.1.6")).passed).isTrue();
    }

    private static MockServerHttpRequest login(String ip) {
        return post("/api/auth/login", ip);
    }

    private static MockServerHttpRequest post(String path, String ip) {
        return MockServerHttpRequest.post(path).header("X-Real-IP", ip).build();
    }

    private static MockServerHttpRequest fromRemote(String ip) {
        return MockServerHttpRequest.post("/api/auth/login").remoteAddress(new InetSocketAddress(ip, 5000)).build();
    }

    private Result run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Result result = new Result(exchange);
        GatewayFilterChain chain = ex -> {
            result.passed = true;
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return result;
    }

    private static final class Result {
        private final MockServerWebExchange exchange;
        private boolean passed;

        private Result(MockServerWebExchange exchange) {
            this.exchange = exchange;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
