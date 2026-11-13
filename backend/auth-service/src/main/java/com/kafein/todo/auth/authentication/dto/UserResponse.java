package com.kafein.todo.auth.authentication.dto;

import com.kafein.todo.auth.user.User;
import com.kafein.todo.common.security.Role;

/** Kullanıcı özeti (docs/06: {@code {id, email, fullName, role}}). Şifre hash'i asla dönmez. */
public record UserResponse(Long id, String email, String fullName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
