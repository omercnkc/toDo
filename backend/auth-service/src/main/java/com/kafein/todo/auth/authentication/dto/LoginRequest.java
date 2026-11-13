package com.kafein.todo.auth.authentication.dto;

import jakarta.validation.constraints.NotBlank;

/** Girişte şifre politikası uygulanmaz (yalnızca boş olmasın); yanlış şifre 401 alır. */
public record LoginRequest(
        @NotBlank(message = "boş olamaz") String email,
        @NotBlank(message = "boş olamaz") String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
