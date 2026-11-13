package com.kafein.todo.auth.token;

import com.kafein.todo.auth.config.AuthProperties;
import com.kafein.todo.auth.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token üretimi (docs/07): 256 bit rastgele değer; istemciye ham hâli (cookie),
 * veritabanına yalnızca SHA-256 hash'i gider. Rotasyon/iptal adım 2-C'de eklenecek.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bit
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock, AuthProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = properties.refreshToken().ttl();
    }

    /** Kullanıcı için yeni refresh token kaydı oluşturur ve ham token'ı döner (yalnızca cookie'ye yazılmalı). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(rawToken), clock.instant().plus(ttl)));
        return rawToken;
    }

    /** SHA-256 hex (64 karakter). Token zaten yüksek entropili olduğu için tuz/yavaş hash gerekmez. */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 her JVM'de bulunmalı", e);
        }
    }
}
