package com.kafein.todo.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.List;

/**
 * Servislerde (servlet) {@code Authorization: Bearer} token'ını doğrulayıp Spring Security
 * bağlamına {@link AuthenticatedUser} koyan filtre. {@code /internal/**} uçlarında servis token'ı da
 * kabul edilir; o zaman principal {@link ServicePrincipal}, yetki {@code ROLE_SERVICE} olur.
 * <p>
 * Gateway token'ı zaten doğruluyor; servisler yine de kendisi doğrular (savunma derinliği,
 * docs/07-security.md). Gateway'in eklediği {@code X-User-*} header'larına güvenilmez.
 * <p>
 * Token yoksa veya geçersizse istek kimliksiz devam eder; korumalı uçta 401'i Spring Security
 * verir. Böylece açık uçlar (örn. refresh) eski/bozuk bir Authorization header'ı yüzünden kırılmaz.
 * <p>
 * <b>Bean olarak tanımlanmamalı</b> (Spring Boot her Filter bean'ini servlet zincirine de ekler,
 * filtre iki kez çalışır). Güvenlik yapılandırmasında
 * {@code http.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)}
 * ile eklenir.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Log log = LogFactory.getLog(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PREFIX = "/internal/";

    private final JwtService jwtService;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            authenticate(request, header.substring(BEARER_PREFIX.length()).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AuthenticatedUser user = jwtService.parseAccessToken(token);
            setAuthentication(request, user, user.role().authority());
        } catch (InvalidTokenException userTokenError) {
            if (isInternalPath(request)) {
                try {
                    ServicePrincipal service = jwtService.parseServiceToken(token);
                    setAuthentication(request, service, ServicePrincipal.AUTHORITY);
                    return;
                } catch (InvalidTokenException ignored) {
                    // kullanıcı token'ının hatası loglanır
                }
            }
            SecurityContextHolder.clearContext();
            log.debug("JWT reddedildi: " + userTokenError.getMessage());
        }
    }

    /**
     * Servis token'ı yalnızca servisler arası uçlarda geçerlidir (docs/07: "SERVICE rolü dış uçlarda geçersizdir").
     * Böylece dış uçlarda principal her zaman {@link AuthenticatedUser}'dır. {@code ..}, {@code %2e} gibi yol
     * hileleri bu filtreden önce Spring Security'nin {@code StrictHttpFirewall}'ında reddedilir.
     */
    private static boolean isInternalPath(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith(INTERNAL_PREFIX);
    }

    private void setAuthentication(HttpServletRequest request, Object principal, String authority) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        authentication.setDetails(detailsSource.buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
