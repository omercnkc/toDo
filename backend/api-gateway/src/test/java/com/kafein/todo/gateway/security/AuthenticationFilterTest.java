package com.kafein.todo.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private AuthenticationFilter filter;
    private CapturingChain chain;

    @BeforeEach
    void setUp() {
        var properties = new GatewaySecurityProperties(List.of("/api/auth/login", "/api/auth/refresh"));
        var errorWriter = new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules());
        filter = new AuthenticationFilter(properties, errorWriter);
        chain = new CapturingChain();
    }

    @Test
    void openPathPassesWithoutToken() {
        var exchange = exchange(MockServerHttpRequest.post("/api/auth/login"));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNotNull();
    }

    @Test
    void protectedPathWithoutTokenReturns401() {
        var exchange = exchange(MockServerHttpRequest.get("/api/projects"));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":401", "\"error\":\"UNAUTHORIZED\"", "\"path\":\"/api/projects\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer ", "Bearer    "})
    void protectedPathWithInvalidAuthorizationReturns401(String authorization) {
        var exchange = exchange(MockServerHttpRequest.get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, authorization));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPathWithBearerTokenPasses() {
        var exchange = exchange(MockServerHttpRequest.get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token"));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNotNull();
    }

    @Test
    void spoofedUserHeadersAreRemoved() {
        var exchange = exchange(MockServerHttpRequest.post("/api/auth/login")
                .header(AuthenticationFilter.USER_ID_HEADER, "1")
                .header(AuthenticationFilter.USER_ROLE_HEADER, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.passed.getRequest().getHeaders();
        assertThat(forwarded.containsKey(AuthenticationFilter.USER_ID_HEADER)).isFalse();
        assertThat(forwarded.containsKey(AuthenticationFilter.USER_ROLE_HEADER)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/tasks/../internal/tasks/by-project/1",
            "/api/tasks/./1",
            "/api/tasks/%2e%2e/internal/x",
            "/api/tasks/%2E%2E/internal/x",
            "/api/tasks/..%2finternal/x",
            "/api/tasks/..;/internal/x",
            "/api/tasks/..%5cinternal/x"
    })
    void pathTricksReturn400(String rawPath) {
        // get(String) URI şablonu gibi davranıp '%' karakterini '%25' yapar; ham yolu korumak için URI kullan
        var exchange = exchange(MockServerHttpRequest.method(HttpMethod.GET, URI.create(rawPath))
                .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token"));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void openPathMatchIsExact() {
        // /api/auth/login açık; altındaki veya benzeri yollar açık sayılmamalı
        var exchange = exchange(MockServerHttpRequest.get("/api/auth/login/extra"));

        filter.filter(exchange, chain).block();

        assertThat(chain.passed).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private static final class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange passed;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.passed = exchange;
            return Mono.empty();
        }
    }
}
