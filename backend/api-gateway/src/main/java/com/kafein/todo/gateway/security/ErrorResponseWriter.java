package com.kafein.todo.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.error.ApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway'in kendi ürettiği hataları servislerle aynı {@link ApiError} formatında yazar.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        ApiError body = ApiError.of(status.value(), error, message, exchange.getRequest().getPath().value());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
