package com.kafein.todo.common.security;

import java.util.Objects;

/**
 * Access token'dan çözülen kullanıcı. Servislerde Spring Security principal'ı olarak durur;
 * controller'da {@code @AuthenticationPrincipal AuthenticatedUser user} ile alınır.
 */
public record AuthenticatedUser(long id, String email, Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
