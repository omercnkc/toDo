package com.kafein.todo.project.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignAuthForwardingInterceptorTest {

    private final FeignAuthForwardingInterceptor interceptor = new FeignAuthForwardingInterceptor();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void kullaniciIstegindekiTokenIletilir() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer kullanici-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer kullanici-token");
    }

    @Test
    void istekDisindaVeyaTokensizIstekteHeaderEklenmez() {
        RequestTemplate outside = new RequestTemplate();
        interceptor.apply(outside);
        assertThat(outside.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        RequestTemplate noToken = new RequestTemplate();
        interceptor.apply(noToken);
        assertThat(noToken.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }
}
