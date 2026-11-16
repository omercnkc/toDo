package com.kafein.todo.project.outbox;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code outbox_events.event_type}. Kodlar değiştirilmez; yeni olay sona eklenir. */
public enum OutboxEventType implements CodedEnum {
    /** Proje kalıcı silindi → task-service projenin görevlerini siler. */
    PROJECT_DELETED(0),
    /** Üye projeden çıkarıldı → task-service o projedeki atamalarını boşaltır. Payload: {@code userId}. */
    MEMBER_REMOVED(1);

    private final byte code;

    OutboxEventType(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<OutboxEventType> {
        public JpaConverter() {
            super(OutboxEventType.class);
        }
    }
}
