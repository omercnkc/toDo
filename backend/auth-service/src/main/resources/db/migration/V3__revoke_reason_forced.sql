-- Yeni iptal nedeni: 3=FORCED (şifre değişikliği, hesabın pasifleştirilmesi gibi güvenlik olaylarında
-- kullanıcının tüm oturumları kapatılır). Java: RevokeReason. CHECK sınırı genişletilir.

ALTER TABLE refresh_tokens
    DROP CHECK ck_refresh_tokens_revoke_reason,
    ADD CONSTRAINT ck_refresh_tokens_revoke_reason CHECK (revoke_reason <= 3);
