package com.kafein.todo.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "boş olamaz")
        @Size(max = 120, message = "en fazla 120 karakter olabilir")
        String fullName) {
}
