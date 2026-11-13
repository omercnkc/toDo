package com.kafein.todo.auth.token;

import com.kafein.todo.auth.user.User;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private final User user = new User("a@b.com", "hash", "A", Role.USER);

    @Test
    void suresiDolmamisVeIptalEdilmemisTokenAktiftir() {
        assertThat(new RefreshToken(user, "h", NOW.plusSeconds(60)).isActive(NOW)).isTrue();
    }

    @Test
    void suresiDolmusTokenAktifDegildir() {
        assertThat(new RefreshToken(user, "h", NOW).isActive(NOW)).isFalse();
        assertThat(new RefreshToken(user, "h", NOW.minusSeconds(1)).isActive(NOW)).isFalse();
    }

    @Test
    void iptalEdilmisTokenAktifDegildir() {
        RefreshToken token = new RefreshToken(user, "h", NOW.plusSeconds(60));
        token.revoke();

        assertThat(token.isActive(NOW)).isFalse();
    }
}
