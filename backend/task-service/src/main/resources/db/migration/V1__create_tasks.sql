-- task_db ilk şema (bkz. docs/05-database.md)
-- Not: Uygulanmış bir migration dosyası DEĞİŞTİRİLMEZ (Flyway checksum hatası verir); değişiklik yeni V2__... ile yapılır.
-- project_id → project_db.projects.id, assignee_id / reporter_id → auth_db.users.id: mantıksal referans
-- (ayrı veritabanları, FK yok). Proje kalıcı silinince görevleri project-service'in outbox relay'i temizler.

CREATE TABLE tasks (
    id          BIGINT           NOT NULL AUTO_INCREMENT,
    project_id  BIGINT           NOT NULL,
    title       VARCHAR(200)     NOT NULL,
    description TEXT             NULL,
    -- 0=TODO, 1=IN_PROGRESS, 2=DONE (Java: TaskStatus, kodlar değiştirilmez)
    status      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- 0=LOW, 1=MEDIUM, 2=HIGH (Java: TaskPriority)
    priority    TINYINT UNSIGNED NOT NULL DEFAULT 1,
    assignee_id BIGINT           NULL,
    reporter_id BIGINT           NOT NULL,
    due_date    DATE             NULL,
    -- Kolon (status) içindeki sıra, 0'dan başlar; boşluksuz tutulur (docs/05 "Sıralama Stratejisi")
    position    INT              NOT NULL,
    -- Optimistic lock (@Version): aynı görevi aynı anda değiştiren iki istekten biri 409 alır
    version     BIGINT           NOT NULL DEFAULT 0,
    created_at  DATETIME(6)      NOT NULL,
    updated_at  DATETIME(6)      NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT ck_tasks_status CHECK (status <= 2),
    CONSTRAINT ck_tasks_priority CHECK (priority <= 2),
    CONSTRAINT ck_tasks_position CHECK (position >= 0),
    -- Board (kolon + sıra) ve proje bazlı silme sorguları
    INDEX idx_tasks_project_status_position (project_id, status, position),
    -- "Bana atanan açık görevler"; üye çıkarılınca atamanın boşaltılması
    INDEX idx_tasks_assignee_status (assignee_id, status),
    -- Geciken / yaklaşan bitiş tarihleri
    INDEX idx_tasks_due_date (due_date),
    INDEX idx_tasks_project_priority (project_id, priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
