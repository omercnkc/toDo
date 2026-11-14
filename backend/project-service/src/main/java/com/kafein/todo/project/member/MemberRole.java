package com.kafein.todo.project.member;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code project_members.member_role}. Kodlar değiştirilmez; yeni değer sona eklenir. */
public enum MemberRole implements CodedEnum {
    MEMBER(0),
    /** Projeyi oluşturan; düzenler, davet eder, üye çıkarır. Projeden çıkarılamaz. */
    OWNER(1);

    private final byte code;

    MemberRole(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<MemberRole> {
        public JpaConverter() {
            super(MemberRole.class);
        }
    }
}
