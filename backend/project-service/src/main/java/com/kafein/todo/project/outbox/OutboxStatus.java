package com.kafein.todo.project.outbox;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code outbox_events.status}. Kodlar değiştirilmez. */
public enum OutboxStatus implements CodedEnum {
    PENDING(0),
    SENT(1),
    /** Deneme sınırı aşıldı; ERROR loglandı, elle müdahale gerekir. */
    FAILED(2);

    private final byte code;

    OutboxStatus(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<OutboxStatus> {
        public JpaConverter() {
            super(OutboxStatus.class);
        }
    }
}
