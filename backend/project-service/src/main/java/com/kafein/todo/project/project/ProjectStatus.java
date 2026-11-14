package com.kafein.todo.project.project;

import com.kafein.todo.common.persistence.CodedEnum;
import com.kafein.todo.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

/** {@code projects.status}. Kodlar değiştirilmez; yeni değer sona eklenir. */
public enum ProjectStatus implements CodedEnum {
    ACTIVE(0),
    /** Yumuşak silme: proje ve görevleri korunur, düzenleme/davet kapanır; geri alınabilir. */
    ARCHIVED(1);

    private final byte code;

    ProjectStatus(int code) {
        this.code = (byte) code;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Converter
    public static class JpaConverter extends CodedEnumConverter<ProjectStatus> {
        public JpaConverter() {
            super(ProjectStatus.class);
        }
    }
}
