package com.kafein.todo.auth.user.dto;

import com.kafein.todo.auth.authentication.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
        @NotBlank(message = "boş olamaz") String currentPassword,
        @NotNull(message = "boş olamaz") @ValidPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
