package com.kafein.todo.common.security;

import com.kafein.todo.common.persistence.CodedEnum;

/**
 * Kullanıcı rolü. Hem JWT {@code role} claim'inde (ad olarak) hem auth_db {@code users.role}
 * kolonunda (kod olarak) kullanılır. Kodlar değiştirilmez; yeni rol sona eklenir.
 */
public enum Role implements CodedEnum {
    USER(0),
    ADMIN(1);

    private final byte code;

    Role(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    /** Spring Security yetki adı ({@code hasRole('ADMIN')} ile eşleşir). */
    public String authority() {
        return "ROLE_" + name();
    }
}
