package com.kafein.todo.auth.token;

import com.kafein.todo.auth.config.AuthProperties;
import com.kafein.todo.auth.user.User;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AuthProperties properties = new AuthProperties(
            new AuthProperties.Jwt("x", Duration.ofMinutes(15)),
            new AuthProperties.RefreshToken(Duration.ofDays(7), true, Duration.ofSeconds(10)),
            new AuthProperties.Password(4),
            new AuthProperties.Admin(null, null));
    private final RefreshTokenService service =
            new RefreshTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC), properties);

    @Test
    void hamTokenDonerVeritabaninaYalnizcaHashiYazilir() {
        User user = new User("a@b.com", "hash", "A", Role.USER);

        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+"); // 32 bayt, base64url, dolgusuz
        assertThat(saved.getValue().getTokenHash())
                .hasSize(64)
                .isEqualTo(RefreshTokenService.hash(raw))
                .isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getValue().getUser()).isSameAs(user);
    }

    @Test
    void herCagridaFarkliTokenUretilir() {
        User user = new User("a@b.com", "hash", "A", Role.USER);

        assertThat(service.issue(user)).isNotEqualTo(service.issue(user));
    }

    @Test
    void hashBilinenSha256Degeridir() {
        // echo -n "abc" | sha256sum
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
