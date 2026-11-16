-- Transactional Outbox (docs/04 "Proje Kalıcı Silme – Outbox", docs/05 "outbox_events").
-- İş verisiyle (proje silme, üye çıkarma) aynı transaction'da yazılır; relay task-service'e asenkron iletir.

CREATE TABLE outbox_events (
    id              BIGINT           NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(50)      NOT NULL,
    -- Silinen/etkilenen proje id'si; FK yok (proje artık olmayabilir)
    aggregate_id    BIGINT           NOT NULL,
    -- 0=PROJECT_DELETED, 1=MEMBER_REMOVED (Java: OutboxEventType, kodlar değiştirilmez)
    event_type      TINYINT UNSIGNED NOT NULL,
    -- MEMBER_REMOVED: {"userId": 42}
    payload         JSON             NULL,
    -- 0=PENDING, 1=SENT, 2=FAILED (Java: OutboxStatus)
    status          TINYINT UNSIGNED NOT NULL DEFAULT 0,
    attempts        INT              NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)      NOT NULL,
    last_error      VARCHAR(500)     NULL,
    created_at      DATETIME(6)      NOT NULL,
    processed_at    DATETIME(6)      NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_event_type CHECK (event_type <= 1),
    CONSTRAINT ck_outbox_events_status CHECK (status <= 2),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    -- Relay sorgusu: status = PENDING AND next_attempt_at <= now
    INDEX idx_outbox_events_status_next_attempt (status, next_attempt_at),
    -- Günlük temizlik: eski SENT kayıtları
    INDEX idx_outbox_events_status_processed (status, processed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
