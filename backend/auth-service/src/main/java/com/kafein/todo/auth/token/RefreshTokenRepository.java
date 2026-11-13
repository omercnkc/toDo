package com.kafein.todo.auth.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Yenileme için satırı kilitleyerek okur ({@code SELECT ... FOR UPDATE}). Aynı token'la gelen
     * eşzamanlı istekler sırayla işlenir; ikincisi ilkinin rotasyonunu görür.
     * Kullanıcı satırı kilitlenmesin diye {@code user} join edilmez (LAZY yüklenir).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    /** Kullanıcının iptal edilmemiş tüm token'larını iptal eder; etkilenen satır sayısını döner. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revoked = true, t.revokedAt = :now, t.revokeReason = :reason
             where t.user.id = :userId and t.revoked = false""")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("reason") RevokeReason reason,
                                @Param("now") Instant now);
}
