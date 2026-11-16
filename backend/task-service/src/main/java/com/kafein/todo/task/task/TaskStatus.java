package com.kafein.todo.task.task;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code tasks.status} = Kanban kolonu. Kodlar değiştirilmez; yeni değer sona eklenir. */
public enum TaskStatus implements CodedEnum {
    TODO(0),
    IN_PROGRESS(1),
    DONE(2);

    private final byte code;

    TaskStatus(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<TaskStatus> {
        public JpaConverter() {
            super(TaskStatus.class);
        }
    }
}
