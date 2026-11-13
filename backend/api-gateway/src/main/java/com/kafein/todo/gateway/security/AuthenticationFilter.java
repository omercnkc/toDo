package com.kafein.todo.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Route'a eşleşen her isteğe uygulanan kimlik filtresi.
 * <p>
 * Faz 1 (iskelet):
 * <ul>
 *   <li>Yol hilesi içeren istekleri ({@code ..}, kodlanmış nokta/eğik çizgi, {@code ;}) reddeder (400).
 *       Aksi halde {@code /api/tasks/../internal/...} gibi bir istek route'a eşleşip servis tarafında
 *       normalize edilince route edilmeyen {@code /internal/**} uçlarına ulaşabilirdi.</li>
 *   <li>İstemcinin gönderdiği {@code X-User-Id} / {@code X-User-Role} header'larını her zaman siler;
 *       bu header'ları yalnızca gateway üretir.</li>
 *   <li>Açık yollar dışında {@code Authorization: Bearer ...} yoksa 401 döner.</li>
 * </ul>
 * Faz 2 (adım 2-C): token imza/süre doğrulaması ve doğrulanan kullanıcının
 * {@code X-User-*} header'larıyla iletilmesi eklenecek.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    /** Diğer gateway filtrelerinden (yük dengeleme, yönlendirme) önce çalışsın. */
    public static final int ORDER = -100;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> FORBIDDEN_RAW_PATH_PARTS = List.of("%2e", "%2f", "%5c", "\\", ";");

    private final List<PathPattern> openPaths;
    private final ErrorResponseWriter errorWriter;

    public AuthenticationFilter(GatewaySecurityProperties properties, ErrorResponseWriter errorWriter) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.openPaths = properties.openPaths().stream().map(parser::parse).toList();
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (hasPathTrick(request.getURI().getRawPath())) {
            return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Geçersiz istek yolu");
        }

        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        if (isOpenPath(request.getPath().pathWithinApplication())) {
            return chain.filter(sanitizedExchange);
        }

        if (!hasBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Kimlik doğrulaması gerekli");
        }

        // TODO(2-C): token doğrulanacak; geçersizse 401, geçerliyse X-User-Id / X-User-Role eklenecek.
        return chain.filter(sanitizedExchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isOpenPath(PathContainer path) {
        return openPaths.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static boolean hasBearerToken(String authorization) {
        return authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && !authorization.substring(BEARER_PREFIX.length()).isBlank();
    }

    static boolean hasPathTrick(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_RAW_PATH_PARTS.stream().anyMatch(lower::contains)) {
            return true;
        }
        for (String segment : rawPath.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
