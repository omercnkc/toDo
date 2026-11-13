package com.kafein.todo.common.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Güvenlik filtrelerinde (controller dışında) {@link ApiError} yazmak için yardımcı. */
final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    static void write(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), error, message, request.getRequestURI()));
    }
}
