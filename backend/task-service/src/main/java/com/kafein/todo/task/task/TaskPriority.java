package com.kafein.todo.task.task;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/**
 * {@code tasks.priority}. Kodlar önem sırasıyla artar: {@code ORDER BY priority} doğrudan önceliğe göre sıralar.
 * Kodlar değiştirilmez.
 */
public enum TaskPriority implements CodedEnum {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    private final byte code;

    TaskPriority(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<TaskPriority> {
        public JpaConverter() {
            super(TaskPriority.class);
        }
    }
}
