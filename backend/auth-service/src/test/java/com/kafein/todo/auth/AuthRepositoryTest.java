package com.kafein.todo.auth;

import com.kafein.todo.auth.token.RefreshToken;
import com.kafein.todo.auth.token.RefreshTokenRepository;
import com.kafein.todo.auth.user.User;
import com.kafein.todo.auth.user.UserRepository;
import com.kafein.todo.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway şeması + entity eşlemesi gerçek MySQL üzerinde. Her test kendi transaction'ında
 * çalışır ve sonunda geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AuthRepositoryTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private TestEntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void kullaniciKaydedilirVeRolKodOlarakSaklanir() {
        User saved = users.saveAndFlush(new User("admin@ornek.com", "{bcrypt}hash", "Yönetici", Role.ADMIN));
        em.clear();

        Byte roleCode = jdbc.queryForObject("SELECT role FROM users WHERE id = ?", Byte.class, saved.getId());
        assertThat(roleCode).isEqualTo((byte) 1);

        User found = users.findByEmail("admin@ornek.com").orElseThrow();
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void emailNormalizeEdilerekSaklanir() {
        users.saveAndFlush(new User("  Ali.Veli@Ornek.COM ", "hash", "  Ali Veli ", Role.USER));

        assertThat(users.existsByEmail("ali.veli@ornek.com")).isTrue();
        assertThat(users.findByEmail("ali.veli@ornek.com").orElseThrow().getFullName()).isEqualTo("Ali Veli");
    }

    @Test
    void ayniEmailIkinciKezKaydedilemez() {
        users.saveAndFlush(new User("ayse@ornek.com", "hash", "Ayşe", Role.USER));

        assertThatThrownBy(() -> users.saveAndFlush(new User("AYSE@ornek.com", "hash", "Ayşe 2", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void veritabaniBuyukKucukHarfFarkliEmailiDeReddeder() {
        // Uygulama normalize etmeyi unutsa bile collation (ai_ci) + UNIQUE çakışmayı yakalar
        insertUserRaw("mehmet@ornek.com", 0);

        assertThatThrownBy(() -> insertUserRaw("MEHMET@ornek.com", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tanimsizRolKoduVeritabanindaReddedilir() {
        // MySQL CHECK ihlali (hata 3819) Spring'in JDBC katmanında genel DataAccessException olarak gelir
        assertThatThrownBy(() -> insertUserRaw("rol@ornek.com", 2))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_users_role");
    }

    @Test
    void refreshTokenHashIleBulunurVeZamanUtcSaklanir() {
        User user = users.saveAndFlush(new User("zeynep@ornek.com", "hash", "Zeynep", Role.USER));
        Instant expiresAt = Instant.parse("2026-11-20T10:00:00Z");
        refreshTokens.saveAndFlush(new RefreshToken(user, HASH_A, expiresAt));
        em.clear();

        RefreshToken found = refreshTokens.findByTokenHash(HASH_A).orElseThrow();
        assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(found.isRevoked()).isFalse();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());

        // Kolon değeri JVM/MySQL saat diliminden bağımsız olarak UTC duvar saati olmalı
        String raw = jdbc.queryForObject(
                "SELECT DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i:%s') FROM refresh_tokens WHERE token_hash = ?",
                String.class, HASH_A);
        assertThat(raw).isEqualTo("2026-11-20 10:00:00");
    }

    @Test
    void ayniTokenHashIkiKezKaydedilemez() {
        User user = users.saveAndFlush(new User("can@ornek.com", "hash", "Can", Role.USER));
        refreshTokens.saveAndFlush(new RefreshToken(user, HASH_A, Instant.now()));

        assertThatThrownBy(() -> refreshTokens.saveAndFlush(new RefreshToken(user, HASH_A, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void kullaniciSilinceTokenlariDaSilinir() {
        User user = users.saveAndFlush(new User("ece@ornek.com", "hash", "Ece", Role.USER));
        refreshTokens.saveAndFlush(new RefreshToken(user, HASH_A, Instant.now()));
        refreshTokens.saveAndFlush(new RefreshToken(user, HASH_B, Instant.now()));
        em.clear();

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, user.getId());
        assertThat(remaining).isZero();
    }

    private void insertUserRaw(String email, int role) {
        jdbc.update("INSERT INTO users (email, password_hash, full_name, role, enabled, created_at, updated_at) "
                + "VALUES (?, 'hash', 'Ham Kayıt', ?, TRUE, NOW(6), NOW(6))", email, role);
    }
}
