package com.kafein.todo.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Kimlik uçlarına (giriş, kayıt) IP başına sabit pencereli istek sınırı → {@code 429 TOO_MANY_REQUESTS}
 * + {@code Retry-After}. Kaba kuvvet ve toplu hesap açmayı yavaşlatır; e-posta başına hatalı giriş sınırı
 * ayrıca auth-service'te ({@code LoginThrottle}).
 * <p>
 * İstemci adresi: nginx'in yazdığı {@code X-Real-IP} (nginx istemcinin gönderdiğinin üzerine yazar),
 * yoksa bağlantının uzak adresi. Gateway portu (8080) yalnızca bu makineye açık olduğu için
 * {@code X-Real-IP}'yi doğrudan gateway'e gönderip sınırı aşmak dışarıdan mümkün değil (docs/07).
 * Sayaç gateway belleğinde (tek örnek).
 */
@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    /** Kimlik filtresinden önce: sınırı aşan istek için token/Origin işi yapılmaz. */
    public static final int ORDER = AuthenticationFilter.ORDER - 10;
    static final String REAL_IP_HEADER = "X-Real-IP";

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private record Window(Instant start, int count) {
    }

    private final List<PathPattern> paths;
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ErrorResponseWriter errorWriter;
    private final Cache<String, Window> windows;

    @Autowired
    public AuthRateLimitFilter(AuthRateLimitProperties properties, ErrorResponseWriter errorWriter) {
        this(properties, errorWriter, Clock.systemUTC());
    }

    AuthRateLimitFilter(AuthRateLimitProperties properties, ErrorResponseWriter errorWriter, Clock clock) {
        this.paths = properties.paths().stream().map(PathPatternParser.defaultInstance::parse).toList();
        this.maxRequests = properties.maxRequests();
        this.window = properties.window();
        this.clock = clock;
        this.errorWriter = errorWriter;
        this.windows = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        PathContainer path = request.getPath().pathWithinApplication();
        if (request.getMethod() != HttpMethod.POST || paths.stream().noneMatch(p -> p.matches(path))) {
            return chain.filter(exchange);
        }
        String key = clientAddress(request) + " " + path.value();
        Instant now = clock.instant();
        Window current = windows.asMap().compute(key, (k, w) ->
                w == null || !now.isBefore(w.start().plus(window)) ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
        if (current.count() <= maxRequests) {
            return chain.filter(exchange);
        }
        long retryAfter = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
        if (current.count() == maxRequests + 1) {
            log.warn("İstek sınırı aşıldı: {} ({} istek / {})", key, maxRequests, window);
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "Çok fazla istek. Lütfen biraz bekleyip tekrar deneyin.");
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static String clientAddress(ServerHttpRequest request) {
        String realIp = request.getHeaders().getFirst(REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "bilinmiyor" : remote.getAddress().getHostAddress();
    }
}
