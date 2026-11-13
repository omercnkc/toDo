package com.kafein.todo.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(7L, "ayse@ornek.com", Role.ADMIN);

    private final JwtService jwtService =
            new JwtService("test-secret-test-secret-test-secret-123", Duration.ofMinutes(15));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    private final AtomicBoolean chainCalled = new AtomicBoolean();
    private final AtomicReference<Authentication> seenAuthentication = new AtomicReference<>();
    private final FilterChain chain = (request, response) -> {
        chainCalled.set(true);
        seenAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
    };

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gecerliTokenKullaniciyiBaglamaKoyar() throws Exception {
        filter.doFilter(request("Bearer " + jwtService.generateAccessToken(USER)), new MockHttpServletResponse(), chain);

        Authentication authentication = seenAuthentication.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(USER);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void bearerOnekiBuyukKucukHarfDuyarsizdir() throws Exception {
        filter.doFilter(request("bearer " + jwtService.generateAccessToken(USER)), new MockHttpServletResponse(), chain);

        assertThat(seenAuthentication.get()).isNotNull();
    }

    @Test
    void tokenYoksaKimliksizDevamEder() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), chain);

        assertThat(chainCalled).isTrue();
        assertThat(seenAuthentication.get()).isNull();
    }

    @Test
    void gecersizTokendaKimliksizDevamEderVeYanitaDokunmaz() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer bozuk.token.degeri"), response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(seenAuthentication.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void bearerOlmayanSemaYokSayilir() throws Exception {
        filter.doFilter(request("Basic YWxpOjEyMw=="), new MockHttpServletResponse(), chain);

        assertThat(chainCalled).isTrue();
        assertThat(seenAuthentication.get()).isNull();
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }
}
