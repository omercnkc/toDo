package com.kafein.todo.auth.user;

import com.kafein.todo.common.persistence.CodedEnumConverter;
import com.kafein.todo.common.security.Role;
import jakarta.persistence.Converter;

/** {@link Role} ↔ {@code users.role} (TINYINT UNSIGNED). */
@Converter
public class RoleConverter extends CodedEnumConverter<Role> {

    public RoleConverter() {
        super(Role.class);
    }
}
