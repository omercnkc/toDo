package com.kafein.todo.auth.authentication.dto;

import com.kafein.todo.auth.authentication.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "boş olamaz")
        @Email(message = "geçerli bir e-posta adresi olmalı")
        @Size(max = 150, message = "en fazla 150 karakter olabilir")
        String email,

        @NotNull(message = "boş olamaz")
        @ValidPassword
        String password,

        @NotBlank(message = "boş olamaz")
        @Size(max = 120, message = "en fazla 120 karakter olabilir")
        String fullName) {

    /** Şifre loglara/hata ayıklayıcıya sızmasın. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
