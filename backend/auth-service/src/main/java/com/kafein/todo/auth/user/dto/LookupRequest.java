package com.kafein.todo.auth.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /internal/users/lookup}: id listesi → özetler (bulunamayan id'ler sonuçta yer almaz). */
public record LookupRequest(
        @NotEmpty(message = "en az bir id gerekli")
        @Size(max = 200, message = "en fazla 200 id gönderilebilir")
        List<@NotNull(message = "null olamaz") Long> ids) {
}
