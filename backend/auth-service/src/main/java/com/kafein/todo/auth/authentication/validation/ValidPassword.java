package com.kafein.todo.auth.authentication.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Şifre politikası: en az 8 karakter, en fazla 72 bayt (UTF-8).
 * <p>
 * 72 bayt BCrypt'in sınırıdır: fazlası ya sessizce kesilir ya da hata verir. Türkçe karakterler
 * 2 bayt olduğu için sınır karakter değil bayt üzerinden kontrol edilir.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "en az 8 karakter, en fazla 72 bayt olmalı";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
