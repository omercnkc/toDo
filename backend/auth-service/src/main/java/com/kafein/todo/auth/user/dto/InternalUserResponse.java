package com.kafein.todo.auth.user.dto;

import com.kafein.todo.auth.user.User;

/**
 * Servisler arası özet (project-service: davet edilecek kişi var mı/aktif mi, üye adları).
 * Yalnızca {@code /internal/users/**} uçlarından döner.
 */
public record InternalUserResponse(Long id, String email, String fullName, boolean enabled) {

    public static InternalUserResponse from(User user) {
        return new InternalUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEnabled());
    }
}
