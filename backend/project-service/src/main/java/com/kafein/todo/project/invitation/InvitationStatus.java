package com.kafein.todo.project.invitation;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code project_invitations.status}. Kodlar değiştirilmez; yeni değer sona eklenir. */
public enum InvitationStatus implements CodedEnum {
    PENDING(0),
    ACCEPTED(1),
    REJECTED(2),
    /** Gönderen (OWNER/ADMIN) bekleyen daveti geri çekti. */
    CANCELLED(3);

    private final byte code;

    InvitationStatus(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<InvitationStatus> {
        public JpaConverter() {
            super(InvitationStatus.class);
        }
    }
}
