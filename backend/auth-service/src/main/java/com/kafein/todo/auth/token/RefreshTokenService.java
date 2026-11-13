package com.kafein.todo.auth.token;

import com.kafein.todo.auth.config.AuthProperties;
import com.kafein.todo.auth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token yaşam döngüsü (docs/07): üretim, rotasyon, tekrar kullanım tespiti, iptal.
 * İstemciye ham token (cookie), veritabanına yalnızca SHA-256 hash'i gider.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32; // 256 bit
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;
    private final Duration reuseGrace;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock, AuthProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = properties.refreshToken().ttl();
        this.reuseGrace = properties.refreshToken().reuseGrace();
    }

    /** Kullanıcı için yeni refresh token kaydı oluşturur ve ham token'ı döner (yalnızca cookie'ye yazılmalı). */
    @Transactional
    public String issue(User user) {
        return issue(user, clock.instant());
    }

    /**
     * Token'ı doğrular, iptal eder ve yerine yenisini verir (rotasyon).
     * <p>
     * İptal edilmiş bir token gelirse:
     * <ul>
     *   <li>rotasyonla son {@code reuse-grace} içinde iptal edildiyse → eşzamanlı yenileme (iki sekme),
     *       alarm yok, yeni token verilir;</li>
     *   <li>aksi halde → tekrar kullanım: token çalınmış olabilir, kullanıcının <b>tüm</b> token'ları
     *       iptal edilir.</li>
     * </ul>
     * {@code noRollbackFor}: hata fırlatılsa da "tümünü iptal et" kalıcı olmalı.
     *
     * @throws InvalidRefreshTokenException token yok/bilinmiyor/süresi dolmuş/iptal edilmiş veya kullanıcı pasif
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken token = repository.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        User user = token.getUser();

        if (token.isRevoked()) {
            if (token.isRecentlyRotated(now, reuseGrace) && token.isUsableAt(now) && user.isEnabled()) {
                log.debug("Eşzamanlı yenileme (kullanıcı {}), tolerans içinde yeni token verildi", user.getId());
                return new Rotation(user, issue(user, now));
            }
            int revoked = repository.revokeAllActiveByUserId(user.getId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("İptal edilmiş refresh token tekrar kullanıldı (kullanıcı {}); {} aktif token iptal edildi",
                    user.getId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsableAt(now) || !user.isEnabled()) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke(RevokeReason.ROTATED, now);
        return new Rotation(user, issue(user, now));
    }

    /** Çıkış: token'ı iptal eder. Token yok/bilinmiyor/zaten iptal ise sessizce geçer (idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(RevokeReason.LOGOUT, clock.instant()));
    }

    /**
     * Kullanıcının tüm oturumlarını kapatır (şifre değişikliği, pasifleştirme). Elindeki access
     * token'lar süreleri dolana kadar (en fazla 15 dk) geçerli kalır; yenileyemezler.
     *
     * @return iptal edilen token sayısı
     */
    @Transactional
    public int revokeAll(Long userId) {
        return repository.revokeAllActiveByUserId(userId, RevokeReason.FORCED, clock.instant());
    }

    private String issue(User user, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(rawToken), now.plus(ttl)));
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

    /** Rotasyon sonucu: token'ın sahibi ve cookie'ye yazılacak yeni ham token. */
    public record Rotation(User user, String newRawToken) {

        @Override
        public String toString() {
            return "Rotation[userId=" + user.getId() + "]";
        }
    }
}
