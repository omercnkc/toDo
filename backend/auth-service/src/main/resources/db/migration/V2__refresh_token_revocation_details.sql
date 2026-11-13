-- Refresh token iptalinin ne zaman ve neden yapıldığı (bkz. docs/07-security.md "Eşzamanlı yenileme").
-- revoke_reason: 0=ROTATED (yenilemede eskisi), 1=LOGOUT, 2=REUSE_DETECTED (Java: RevokeReason)
-- Rotasyonla yeni iptal edilmiş bir token kısa süre içinde tekrar gelirse (iki sekmenin aynı anda
-- yenilemesi) alarm verilmez; bunu ayırt etmek için zaman ve neden gerekir.

ALTER TABLE refresh_tokens
    ADD COLUMN revoked_at    DATETIME(6)      NULL AFTER revoked,
    ADD COLUMN revoke_reason TINYINT UNSIGNED NULL AFTER revoked_at,
    ADD CONSTRAINT ck_refresh_tokens_revoke_reason CHECK (revoke_reason <= 2);
