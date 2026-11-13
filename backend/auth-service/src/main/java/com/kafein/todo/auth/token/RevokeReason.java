package com.kafein.todo.auth.token;

import com.kafein.todo.common.persistence.CodedEnum;

/** Refresh token neden iptal edildi ({@code refresh_tokens.revoke_reason}; kodlar değiştirilmez). */
public enum RevokeReason implements CodedEnum {
    /** Yenilemede yerine yenisi verildi. */
    ROTATED(0),
    /** Kullanıcı çıkış yaptı. */
    LOGOUT(1),
    /** İptal edilmiş token tekrar kullanıldı; kullanıcının tüm token'ları iptal edildi. */
    REUSE_DETECTED(2);

    private final byte code;

    RevokeReason(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }
}
