package com.kafein.todo.auth.authentication.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void sekizKarakterVeUzeriGecerlidir() {
        assertThat(validator.isValid("12345678", null)).isTrue();
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void sekizKarakterdenKisaGecersizdir() {
        assertThat(validator.isValid("1234567", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
    }

    @Test
    void yetmisIkiBaytiAsanGecersizdir() {
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
        // 40 karakter ama 80 bayt: Türkçe karakterler UTF-8'de 2 bayt
        assertThat(validator.isValid("ş".repeat(40), null)).isFalse();
        assertThat(validator.isValid("ş".repeat(36), null)).isTrue(); // 72 bayt
    }

    @Test
    void nullBaskaKuralaBirakilir() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
