-- Proje bazlı board kilidi (docs/05 "Sıralama Stratejisi").
-- Bir kolondaki pozisyonları değiştiren her işlem (oluşturma, durum değiştirme, taşıma, silme) transaction
-- başında projenin satırını kilitler:
--   INSERT INTO task_board_locks (project_id) VALUES (?) ON DUPLICATE KEY UPDATE project_id = project_id
-- Satır yoksa oluşturulur, varsa doğrudan X kilidi alınır (INSERT IGNORE + SELECT FOR UPDATE'teki S→X
-- yükseltmesi kilitlenmeye yol açabilir). Böylece aynı projedeki eşzamanlı işlemler sıraya girer, farklı
-- projeler birbirini beklemez; pozisyonlar çakışmaz ve boşluksuz kalır.
-- task_db'de projects tablosu yok (proje project-service'te), kilit satırı bu yüzden ayrı tabloda.

CREATE TABLE task_board_locks (
    project_id BIGINT NOT NULL,
    CONSTRAINT pk_task_board_locks PRIMARY KEY (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
