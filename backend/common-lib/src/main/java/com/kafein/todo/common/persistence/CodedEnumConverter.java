package com.kafein.todo.common.persistence;

import jakarta.persistence.AttributeConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link CodedEnum} ↔ {@code Byte} JPA dönüştürücüsünün ortak gövdesi.
 * Servisler enum başına boş bir alt sınıf tanımlar:
 * <pre>{@code
 * @Converter
 * public class RoleConverter extends CodedEnumConverter<Role> {
 *     public RoleConverter() { super(Role.class); }
 * }
 * }</pre>
 * Veritabanında bilinmeyen bir kod okunursa sessizce {@code null} dönmek yerine hata fırlatır.
 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, Byte> {

    private final Class<E> type;
    private final Map<Byte, E> byCode = new HashMap<>();

    protected CodedEnumConverter(Class<E> type) {
        this.type = type;
        for (E constant : type.getEnumConstants()) {
            E previous = byCode.put(constant.getCode(), constant);
            if (previous != null) {
                throw new IllegalStateException("%s içinde %s ve %s aynı kodu (%d) kullanıyor"
                        .formatted(type.getSimpleName(), previous, constant, constant.getCode()));
            }
        }
    }

    @Override
    public Byte convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public E convertToEntityAttribute(Byte code) {
        if (code == null) {
            return null;
        }
        E value = byCode.get(code);
        if (value == null) {
            throw new IllegalArgumentException("Bilinmeyen %s kodu: %d".formatted(type.getSimpleName(), code));
        }
        return value;
    }
}
