package com.kafein.todo.common.security;

import java.util.Objects;

/**
 * Servis token'ıyla gelen çağıran servis (örn. project-service outbox relay'i). Kullanıcı değildir:
 * {@link Role}'de yer almaz, veritabanına yazılmaz, yalnızca {@code /internal/**} uçlarında kabul edilir
 * ({@link JwtAuthenticationFilter}).
 *
 * @param name çağıran servisin adı ({@code sub} claim'i), loglar için
 */
public record ServicePrincipal(String name) {

    /** JWT {@code role} claim değeri. */
    public static final String ROLE = "SERVICE";

    /** Spring Security yetki adı ({@code hasRole('SERVICE')} ile eşleşir). */
    public static final String AUTHORITY = "ROLE_" + ROLE;

    public ServicePrincipal {
        Objects.requireNonNull(name, "name");
    }
}
