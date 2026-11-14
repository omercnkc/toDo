-- project_db ilk şema (bkz. docs/05-database.md)
-- Not: Uygulanmış bir migration dosyası DEĞİŞTİRİLMEZ (Flyway checksum hatası verir); değişiklik yeni V2__... ile yapılır.
-- outbox_events tablosu kalıcı silmeyle birlikte Faz 4'te (V2) eklenir.
-- Kullanıcı id'leri auth_db.users.id'ye mantıksal referanstır (ayrı veritabanı, FK yok).

CREATE TABLE projects (
    id          BIGINT           NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)     NOT NULL,
    description TEXT             NULL,
    owner_id    BIGINT           NOT NULL,
    -- 0=ACTIVE, 1=ARCHIVED (Java: ProjectStatus, kodlar değiştirilmez)
    status      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME(6)      NOT NULL,
    updated_at  DATETIME(6)      NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT ck_projects_status CHECK (status <= 1),
    INDEX idx_projects_owner_id (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_members (
    id          BIGINT           NOT NULL AUTO_INCREMENT,
    project_id  BIGINT           NOT NULL,
    user_id     BIGINT           NOT NULL,
    -- 0=MEMBER, 1=OWNER (Java: MemberRole)
    member_role TINYINT UNSIGNED NOT NULL DEFAULT 0,
    joined_at   DATETIME(6)      NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    -- (project_id, user_id) indeksi aynı zamanda "projenin üyeleri" sorgusunu karşılar
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_members_role CHECK (member_role <= 1),
    -- "kullanıcının projeleri" sorgusu için
    INDEX idx_project_members_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_invitations (
    id           BIGINT           NOT NULL AUTO_INCREMENT,
    project_id   BIGINT           NOT NULL,
    invitee_id   BIGINT           NOT NULL,
    inviter_id   BIGINT           NOT NULL,
    -- 0=PENDING, 1=ACCEPTED, 2=REJECTED, 3=CANCELLED (Java: InvitationStatus)
    status       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- Yalnızca bekleyen davette 1, diğerlerinde NULL. MySQL'de kısmi unique indeks olmadığı için:
    -- NULL'lar unique kontrolüne girmez → aynı kişiye aynı projede aynı anda tek BEKLEYEN davet,
    -- geçmiş (yanıtlanmış/iptal) davetler engellemez.
    pending_flag TINYINT GENERATED ALWAYS AS (IF(status = 0, 1, NULL)) STORED,
    created_at   DATETIME(6)      NOT NULL,
    responded_at DATETIME(6)      NULL,
    CONSTRAINT pk_project_invitations PRIMARY KEY (id),
    CONSTRAINT uk_project_invitations_pending UNIQUE (project_id, invitee_id, pending_flag),
    CONSTRAINT fk_project_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_invitations_status CHECK (status <= 3),
    -- "bana gelen bekleyen davetler" sorgusu için
    INDEX idx_project_invitations_invitee_status (invitee_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
