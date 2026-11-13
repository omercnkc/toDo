-- auth_db ilk şema (bkz. docs/05-database.md)
-- Not: Uygulanmış bir migration dosyası DEĞİŞTİRİLMEZ (Flyway checksum hatası verir); değişiklik yeni V2__... ile yapılır.

CREATE TABLE users (
    id            BIGINT           NOT NULL AUTO_INCREMENT,
    -- utf8mb4_0900_ai_ci büyük/küçük harf duyarsız: Ali@x.com ile ali@x.com aynı e-posta sayılır
    email         VARCHAR(150)     NOT NULL,
    password_hash VARCHAR(100)     NOT NULL,
    full_name     VARCHAR(120)     NOT NULL,
    -- 0=USER, 1=ADMIN (Java: common-lib Role, kodlar değiştirilmez)
    role          TINYINT UNSIGNED NOT NULL DEFAULT 0,
    enabled       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)      NOT NULL,
    updated_at    DATETIME(6)      NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    -- Ham token saklanmaz; SHA-256 hex (64 karakter). ascii_bin: küçük indeks, birebir karşılaştırma
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
