package com.kafein.todo.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servisler arası çağrıya kullanıcının {@code Authorization} header'ını ekler (docs/07: kullanıcı bağlamı korunur).
 * Kullanıcı isteği dışında (zamanlanmış işler) header yoktur; o çağrılar servis JWT'si kullanır (Faz 4-D).
 * <p>
 * OpenFeign kullanan servlet servislerine {@link CommonFeignAutoConfiguration} ile otomatik eklenir.
 */
public class FeignAuthForwardingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}
