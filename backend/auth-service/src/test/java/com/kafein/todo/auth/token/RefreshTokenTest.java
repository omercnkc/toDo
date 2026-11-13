package com.kafein.todo.auth.token;

import com.kafein.todo.auth.user.User;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(10);
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
    void iptalEdilmisTokenAktifDegildirVeNedeniSaklanir() {
        RefreshToken token = new RefreshToken(user, "h", NOW.plusSeconds(60));
        token.revoke(RevokeReason.LOGOUT, NOW);

        assertThat(token.isActive(NOW)).isFalse();
        assertThat(token.getRevokeReason()).isEqualTo(RevokeReason.LOGOUT);
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void ikinciIptalIlkIptalBilgisiniDegistirmez() {
        RefreshToken token = new RefreshToken(user, "h", NOW.plusSeconds(60));
        token.revoke(RevokeReason.ROTATED, NOW);
        token.revoke(RevokeReason.REUSE_DETECTED, NOW.plusSeconds(5));

        assertThat(token.getRevokeReason()).isEqualTo(RevokeReason.ROTATED);
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void rotasyonlaYeniIptalEdilenToleransIcindedir() {
        RefreshToken token = new RefreshToken(user, "h", NOW.plusSeconds(600));
        token.revoke(RevokeReason.ROTATED, NOW);

        assertThat(token.isRecentlyRotated(NOW.plusSeconds(10), GRACE)).isTrue();
        assertThat(token.isRecentlyRotated(NOW.plusSeconds(11), GRACE)).isFalse();
    }

    @Test
    void logoutIleIptalEdilenToleransaGirmez() {
        RefreshToken token = new RefreshToken(user, "h", NOW.plusSeconds(600));
        token.revoke(RevokeReason.LOGOUT, NOW);

        assertThat(token.isRecentlyRotated(NOW, GRACE)).isFalse();
    }

    @Test
    void iptalEdilmemisTokenRotasyonSayilmaz() {
        assertThat(new RefreshToken(user, "h", NOW.plusSeconds(600)).isRecentlyRotated(NOW, GRACE)).isFalse();
    }
}
