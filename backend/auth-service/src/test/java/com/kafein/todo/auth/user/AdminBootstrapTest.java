package com.kafein.todo.auth.user;

import com.kafein.todo.auth.config.AuthProperties;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void adminYoksaOlusturulur() {
        bootstrap(" Admin@Todo.local ", "Admin.Sifre1").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@todo.local");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("Admin.Sifre1", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void adminVarsaDokunulmaz() {
        when(users.existsByEmail("admin@todo.local")).thenReturn(true);

        bootstrap("admin@todo.local", "Admin.Sifre1").run(null);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void ayarYoksaAtlanir() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void zayifSifreServisiDurdurur() {
        assertThatThrownBy(() -> bootstrap("admin@todo.local", "kisa").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void sifreToStringIleSizmaz() {
        assertThat(new AuthProperties.Admin("admin@todo.local", "Admin.Sifre1").toString())
                .doesNotContain("Admin.Sifre1");
    }

    private AdminBootstrap bootstrap(String email, String password) {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt("x", Duration.ofMinutes(15)),
                new AuthProperties.RefreshToken(Duration.ofDays(7), true, Duration.ofSeconds(10)),
                new AuthProperties.Password(4),
                new AuthProperties.Admin(email, password));
        return new AdminBootstrap(users, passwordEncoder, properties);
    }
}
