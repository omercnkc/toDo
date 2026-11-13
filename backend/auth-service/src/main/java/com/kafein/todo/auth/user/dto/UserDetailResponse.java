package com.kafein.todo.auth.user.dto;

import com.kafein.todo.auth.user.User;
import com.kafein.todo.common.security.Role;

import java.time.Instant;

/** Kullanıcı detayı: kişinin kendi profili ve ADMIN ekranları için. */
public record UserDetailResponse(Long id, String email, String fullName, Role role, boolean enabled,
                                 Instant createdAt, Instant updatedAt) {

    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
