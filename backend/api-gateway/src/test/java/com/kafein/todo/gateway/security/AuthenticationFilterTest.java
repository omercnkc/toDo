package com.kafein.todo.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final AuthenticatedUser USER = new AuthenticatedUser(42L, "ali@ornek.com", Role.USER);

    private final JwtService issuer = new JwtService(SECRET, Duration.ofMinutes(15));
    private AuthenticationFilter filter;
    private CapturingChain chain;

    @BeforeEach
    void setUp() {
        var properties = new GatewaySecurityProperties(
                List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout"),
                SECRET,
                List.of(ALLOWED_ORIGIN),
                List.of("/api/auth/refresh", "/api/auth/logout"));
        var errorWriter = new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules());
        filter = new AuthenticationFilter(properties, JwtService.verifierOnly(SECRET, Clock.systemUTC()), errorWriter);
        chain = new CapturingChain();
    }

    // ---------- açık yollar ----------

    @Test
    void acikYolTokensizGecer() {
        var exchange = run(MockServerHttpRequest.post("/api/auth/login"));

        assertThat(chain.passed).isNotNull();
        assertThat(forwardedHeaders().containsKey(AuthenticationFilter.USER_ID_HEADER)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void acikYolEslesmesiBirebirdir() {
        var exchange = run(MockServerHttpRequest.get("/api/auth/login/extra"));

        assertRejected(exchange, HttpStatus.UNAUTHORIZED);
    }

    // ---------- token doğrulama ----------

    @Test
    void gecerliTokenKullaniciyiHeaderlaIletir() {
        run(MockServerHttpRequest.get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(USER)));

        assertThat(chain.passed).isNotNull();
        assertThat(forwardedHeaders().getFirst(AuthenticationFilter.USER_ID_HEADER)).isEqualTo("42");
        assertThat(forwardedHeaders().getFirst(AuthenticationFilter.USER_ROLE_HEADER)).isEqualTo("USER");
    }

    @Test
    void tokenYoksa401() {
        var exchange = run(MockServerHttpRequest.get("/api/projects"));

        assertRejected(exchange, HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":401", "\"error\":\"UNAUTHORIZED\"", "\"path\":\"/api/projects\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer ", "Bearer    ", "Bearer bozuk.token.degeri"})
    void gecersizAuthorization401(String authorization) {
        var exchange = run(MockServerHttpRequest.get("/api/projects").header(HttpHeaders.AUTHORIZATION, authorization));

        assertRejected(exchange, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void baskaAnahtarlaImzalanmisToken401() {
        JwtService other = new JwtService("baska-secret-baska-secret-baska-secret", Duration.ofMinutes(15));
        var exchange = run(MockServerHttpRequest.get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.generateAccessToken(USER)));

        assertRejected(exchange, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suresiDolmusToken401() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        JwtService pastIssuer = new JwtService(SECRET, Duration.ofMinutes(15), Clock.fixed(past, ZoneOffset.UTC));
        var exchange = run(MockServerHttpRequest.get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pastIssuer.generateAccessToken(USER)));

        assertRejected(exchange, HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("süresi dolmuş");
    }

    @Test
    void sahteKimlikHeaderlariGercekDegerlerleEzilir() {
        run(MockServerHttpRequest.get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer(USER))
                .header(AuthenticationFilter.USER_ID_HEADER, "1")
                .header(AuthenticationFilter.USER_ROLE_HEADER, "ADMIN"));

        assertThat(forwardedHeaders().get(AuthenticationFilter.USER_ID_HEADER)).containsExactly("42");
        assertThat(forwardedHeaders().get(AuthenticationFilter.USER_ROLE_HEADER)).containsExactly("USER");
    }

    @Test
    void acikYoldaSahteKimlikHeaderlariSilinir() {
        run(MockServerHttpRequest.post("/api/auth/login")
                .header(AuthenticationFilter.USER_ID_HEADER, "1")
                .header(AuthenticationFilter.USER_ROLE_HEADER, "ADMIN"));

        assertThat(forwardedHeaders().containsKey(AuthenticationFilter.USER_ID_HEADER)).isFalse();
        assertThat(forwardedHeaders().containsKey(AuthenticationFilter.USER_ROLE_HEADER)).isFalse();
    }

    // ---------- Origin kontrolü (cookie'li yollar) ----------

    @Test
    void refreshIzinliOriginleGecer() {
        run(MockServerHttpRequest.post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN));

        assertThat(chain.passed).isNotNull();
    }

    @Test
    void refreshOriginsizGecer() {
        run(MockServerHttpRequest.post("/api/auth/refresh"));

        assertThat(chain.passed).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/refresh", "/api/auth/logout"})
    void cookieliYolaYabanciOrigin403(String path) {
        var exchange = run(MockServerHttpRequest.post(path).header(HttpHeaders.ORIGIN, "http://evil.example"));

        assertRejected(exchange, HttpStatus.FORBIDDEN);
    }

    @Test
    void nullOrigin403() {
        var exchange = run(MockServerHttpRequest.post("/api/auth/refresh").header(HttpHeaders.ORIGIN, "null"));

        assertRejected(exchange, HttpStatus.FORBIDDEN);
    }

    @Test
    void cookiesizYoldaOriginKontroluYapilmaz() {
        // login cookie ile kimlik doğrulamaz; çapraz origin'i CORS yönetir
        run(MockServerHttpRequest.post("/api/auth/login").header(HttpHeaders.ORIGIN, "http://evil.example"));

        assertThat(chain.passed).isNotNull();
    }

    // ---------- yol hilesi ----------

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
    void yolHilesi400(String rawPath) {
        // get(String) URI şablonu gibi davranıp '%' karakterini '%25' yapar; ham yolu korumak için URI kullan
        var exchange = run(MockServerHttpRequest.method(HttpMethod.GET, URI.create(rawPath))
                .header(HttpHeaders.AUTHORIZATION, bearer(USER)));

        assertRejected(exchange, HttpStatus.BAD_REQUEST);
    }

    // ---------- yardımcılar ----------

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private void assertRejected(MockServerWebExchange exchange, HttpStatus status) {
        assertThat(chain.passed).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
    }

    private HttpHeaders forwardedHeaders() {
        return chain.passed.getRequest().getHeaders();
    }

    private String bearer(AuthenticatedUser user) {
        return "Bearer " + issuer.generateAccessToken(user);
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
