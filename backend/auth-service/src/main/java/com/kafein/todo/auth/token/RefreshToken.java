package com.kafein.todo.auth.token;

import com.kafein.todo.auth.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Refresh token kaydı. Ham token yalnızca istemcideki httpOnly cookie'de durur;
 * burada SHA-256 hash'i saklanır (DB sızsa bile token'lar kullanılamaz).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Convert(converter = RevokeReasonConverter.class)
    @Column(name = "revoke_reason")
    private RevokeReason revokeReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** İptal edilmemiş ve süresi dolmamışsa kullanılabilir. */
    public boolean isActive(Instant now) {
        return !revoked && isUsableAt(now);
    }

    /** Süresi dolmamış mı (iptal durumuna bakmaz). */
    public boolean isUsableAt(Instant now) {
        return expiresAt.isAfter(now);
    }

    /** Zaten iptal edilmişse ilk iptalin zamanı ve nedeni korunur. */
    public void revoke(RevokeReason reason, Instant now) {
        if (revoked) {
            return;
        }
        this.revoked = true;
        this.revokedAt = Objects.requireNonNull(now, "now");
        this.revokeReason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Rotasyonla az önce ({@code grace} içinde) iptal edilmiş mi? Aynı anda iki yenileme isteği
     * geldiğinde (iki sekme) ikincisi bu durumdadır; tekrar kullanım saldırısı sayılmaz.
     */
    public boolean isRecentlyRotated(Instant now, Duration grace) {
        return revoked && revokeReason == RevokeReason.ROTATED && revokedAt != null
                && !now.isAfter(revokedAt.plus(grace));
    }
}
