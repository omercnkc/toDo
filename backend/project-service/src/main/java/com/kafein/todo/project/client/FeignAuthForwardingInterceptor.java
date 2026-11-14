package com.kafein.todo.project.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servisler arası çağrıya kullanıcının {@code Authorization} header'ını ekler (docs/07: kullanıcı bağlamı korunur).
 * Kullanıcı isteği dışında (zamanlanmış işler) header yoktur; o çağrılar Faz 4'te servis JWT'si kullanacak.
 * <p>
 * Not: task-service ve dashboard-service de aynısına ihtiyaç duyunca common-lib'e taşınacak.
 */
@Component
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
