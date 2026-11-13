package com.kafein.todo.auth.user.dto;

import com.kafein.todo.common.security.Role;

/**
 * ADMIN güncellemesi (docs/06: rol, enabled). Alanlar isteğe bağlı: gönderilmeyen (null) alan değişmez.
 * En az biri dolu olmalı.
 */
public record AdminUpdateUserRequest(Role role, Boolean enabled) {

    public boolean isEmpty() {
        return role == null && enabled == null;
    }
}
