package com.kafein.todo.gateway.security;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.InvalidTokenException;
import com.kafein.todo.common.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Route'a eşleşen her isteğe uygulanan kimlik filtresi (docs/07-security.md). Sırasıyla:
 * <ol>
 *   <li>Yol hilesi ({@code ..}, kodlanmış nokta/eğik çizgi, {@code ;}) → 400. Aksi halde
 *       {@code /api/tasks/../internal/...} route'a eşleşip servis tarafında normalize edilince
 *       route edilmeyen {@code /internal/**} uçlarına ulaşabilirdi.</li>
 *   <li>İstemcinin gönderdiği {@code X-User-Id} / {@code X-User-Role} her zaman silinir; bunları
 *       yalnızca gateway, doğruladığı token'dan üretir.</li>
 *   <li>Cookie ile çalışan yollarda (refresh/logout) {@code Origin} izinli değilse → 403 (CSRF savunması;
 *       SameSite=Strict'e ek katman).</li>
 *   <li>Açık yollar token istemeden geçer.</li>
 *   <li>Diğerlerinde Bearer token zorunlu ve geçerli olmalı → aksi halde 401; geçerliyse kullanıcı
 *       {@code X-User-*} header'larıyla servise iletilir.</li>
 * </ol>
 * Servisler token'ı yine kendileri doğrular (savunma derinliği); header'lar kolaylık içindir.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    /** Diğer gateway filtrelerinden (yük dengeleme, yönlendirme) önce çalışsın. */
    public static final int ORDER = -100;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> FORBIDDEN_RAW_PATH_PARTS = List.of("%2e", "%2f", "%5c", "\\", ";");

    private final List<PathPattern> openPaths;
    private final List<PathPattern> cookieAuthPaths;
    private final List<String> allowedOrigins;
    private final JwtService jwtService;
    private final ErrorResponseWriter errorWriter;

    public AuthenticationFilter(GatewaySecurityProperties properties, JwtService jwtService,
                                ErrorResponseWriter errorWriter) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.openPaths = properties.openPaths().stream().map(parser::parse).toList();
        this.cookieAuthPaths = properties.cookieAuthPaths().stream().map(parser::parse).toList();
        this.allowedOrigins = properties.allowedOrigins();
        this.jwtService = jwtService;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        PathContainer path = request.getPath().pathWithinApplication();

        if (hasPathTrick(request.getURI().getRawPath())) {
            return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Geçersiz istek yolu");
        }

        if (matchesAny(cookieAuthPaths, path) && !isOriginAllowed(request.getHeaders().getOrigin())) {
            log.warn("İzin verilmeyen Origin ile cookie'li istek reddedildi: {} {}",
                    request.getHeaders().getOrigin(), path.value());
            return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "İzin verilmeyen istek kaynağı");
        }

        if (matchesAny(openPaths, path)) {
            return chain.filter(withUserHeaders(exchange, null));
        }

        String token = bearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Kimlik doğrulaması gerekli");
        }
        AuthenticatedUser user;
        try {
            user = jwtService.parseAccessToken(token);
        } catch (InvalidTokenException e) {
            log.debug("Token reddedildi: {}", e.getMessage());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Oturum geçersiz veya süresi dolmuş");
        }
        return chain.filter(withUserHeaders(exchange, user));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** İstemciden gelen X-User-* her zaman silinir; kullanıcı varsa doğrulanmış değerler yazılır. */
    private static ServerWebExchange withUserHeaders(ServerWebExchange exchange, AuthenticatedUser user) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    if (user != null) {
                        headers.set(USER_ID_HEADER, Long.toString(user.id()));
                        headers.set(USER_ROLE_HEADER, user.role().name());
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /**
     * Origin yoksa izin verilir: tarayıcılar POST'ta Origin'i her zaman gönderir; göndermeyen
     * tarayıcı dışı istemcilerin (curl, testler) kurbanın cookie'sine erişimi zaten yoktur.
     * {@code Origin: null} (sandbox iframe, dosya) izinli sayılmaz.
     */
    private boolean isOriginAllowed(String origin) {
        return origin == null || allowedOrigins.contains(origin);
    }

    private static boolean matchesAny(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
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
