package com.kafein.todo.common.persistence;

import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodedEnumConverterTest {

    enum Renk implements CodedEnum {
        KIRMIZI(0), MAVI(5);

        private final byte code;

        Renk(int code) {
            this.code = (byte) code;
        }

        @Override
        public byte getCode() {
            return code;
        }
    }

    enum CakisanKod implements CodedEnum {
        A, B;

        @Override
        public byte getCode() {
            return 1;
        }
    }

    static class RenkConverter extends CodedEnumConverter<Renk> {
        RenkConverter() {
            super(Renk.class);
        }
    }

    static class CakisanKodConverter extends CodedEnumConverter<CakisanKod> {
        CakisanKodConverter() {
            super(CakisanKod.class);
        }
    }

    static class RoleConverter extends CodedEnumConverter<Role> {
        RoleConverter() {
            super(Role.class);
        }
    }

    private final RenkConverter converter = new RenkConverter();

    @Test
    void enumKodaVeKodEnumaDonusur() {
        assertThat(converter.convertToDatabaseColumn(Renk.MAVI)).isEqualTo((byte) 5);
        assertThat(converter.convertToEntityAttribute((byte) 5)).isEqualTo(Renk.MAVI);
        assertThat(converter.convertToEntityAttribute((byte) 0)).isEqualTo(Renk.KIRMIZI);
    }

    @Test
    void nullNullKalir() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void bilinmeyenKodHataVerir() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute((byte) 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Renk")
                .hasMessageContaining("9");
    }

    @Test
    void ayniKoduKullananIkiDegerKabulEdilmez() {
        assertThatThrownBy(CakisanKodConverter::new).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rolKodlariSabittir() {
        // Bu kodlar auth_db.users.role kolonunda saklı: DEĞİŞTİRİLMEMELİ (docs/05-database.md)
        RoleConverter roles = new RoleConverter();
        assertThat(roles.convertToDatabaseColumn(Role.USER)).isEqualTo((byte) 0);
        assertThat(roles.convertToDatabaseColumn(Role.ADMIN)).isEqualTo((byte) 1);
    }
}
