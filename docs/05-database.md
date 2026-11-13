# 05 – Veritabanı

Tek MySQL 8 sunucusu, 3 şema, her servisin kendi DB kullanıcısı yalnızca kendi şemasına yetkili. Charset `utf8mb4`, engine InnoDB.
Şema yönetimi Flyway (`src/main/resources/db/migration/V1__init.sql`), JPA `ddl-auto=validate`.
`dashboard-service` DB kullanmaz.

## ER Diyagramı (şemalar arası ilişkiler yalnızca mantıksal)
```mermaid
erDiagram
  USERS ||--o{ REFRESH_TOKENS : has
  USERS ||--o{ PROJECT_MEMBERS : "user_id (mantıksal)"
  PROJECTS ||--o{ PROJECT_MEMBERS : has
  PROJECTS ||--o{ TASKS : "project_id (mantıksal)"
  USERS ||--o{ TASKS : "assignee_id / reporter_id (mantıksal)"
```

## Durum / Rol Kolonları (Enum Stratejisi)
Sabit değerli alanlar (`role`, `status`, `member_role`, `priority`, davet `status`) veritabanında `TINYINT UNSIGNED` olarak tutulur (az yer, küçük indeks). Karşılık tablosu ve MySQL `ENUM` tipi kullanılmaz.
- Rakamların anlamı yalnızca Java enum'larında yaşar; enum'lar `code` (byte) alanı taşır ve JPA `AttributeConverter` ile çevrilir. `@Enumerated(ORDINAL)` kullanılmaz (enum sırası değişirse veri bozulur).
- Rakamlar bir kez yazıldıktan sonra **değiştirilmez**; yeni değerler sona eklenir (örn. `3=BLOCKED`).
- API sözleşmesi değişmez: istek/yanıtlarda `"TODO"`, `"ADMIN"` gibi metin değerleri kullanılır, rakam dışarı sızmaz.
- Geçersiz değer koruması kodda (converter bilinmeyen rakamda hata fırlatır) ve isteğe bağlı `CHECK (status <= 2)` ile yapılır.

## auth_db
**users**
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| email | VARCHAR(150) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(100) | NOT NULL (BCrypt) |
| full_name | VARCHAR(120) | NOT NULL |
| role | TINYINT UNSIGNED | NOT NULL, default 0 (0=`USER`, 1=`ADMIN`) |
| enabled | BOOLEAN | NOT NULL, default true |
| created_at / updated_at | DATETIME(6) | NOT NULL |

**refresh_tokens**
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users.id (aynı şema), INDEX |
| token_hash | CHAR(64) | UNIQUE (SHA-256; ham token saklanmaz) |
| expires_at | DATETIME(6) | NOT NULL |
| revoked | BOOLEAN | NOT NULL, default false |
| revoked_at | DATETIME(6) | NULL (V2) |
| revoke_reason | TINYINT UNSIGNED | NULL (V2; 0=`ROTATED`, 1=`LOGOUT`, 2=`REUSE_DETECTED`; V3: 3=`FORCED` — şifre değişikliği / pasifleştirme) |
| created_at | DATETIME(6) | NOT NULL |

## project_db
**projects**
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(150) | NOT NULL |
| description | TEXT | NULL |
| owner_id | BIGINT | NOT NULL (auth_db.users.id, mantıksal), INDEX |
| status | TINYINT UNSIGNED | NOT NULL, default 0 (0=`ACTIVE`, 1=`ARCHIVED`) |
| created_at / updated_at | DATETIME(6) | NOT NULL |

**project_members**
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| project_id | BIGINT | FK → projects.id ON DELETE CASCADE |
| user_id | BIGINT | NOT NULL, INDEX |
| member_role | TINYINT UNSIGNED | NOT NULL, default 0 (0=`MEMBER`, 1=`OWNER`) |
| joined_at | DATETIME(6) | NOT NULL |
| | | UNIQUE (project_id, user_id) |

**project_invitations** (davet/onay akışı; kullanıcı onaylamadan üyelik oluşmaz)
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| project_id | BIGINT | FK → projects.id ON DELETE CASCADE |
| invitee_id | BIGINT | NOT NULL (davet edilen; auth_db.users.id, mantıksal) |
| inviter_id | BIGINT | NOT NULL (daveti gönderen; mantıksal) |
| status | TINYINT UNSIGNED | NOT NULL, default 0 (0=`PENDING`, 1=`ACCEPTED`, 2=`REJECTED`, 3=`CANCELLED`) |
| pending_flag | TINYINT | GENERATED ALWAYS AS (IF(status = 0, 1, NULL)) STORED |
| created_at | DATETIME(6) | NOT NULL |
| responded_at | DATETIME(6) | NULL (kabul/red/iptal zamanı) |
| | | UNIQUE (project_id, invitee_id, pending_flag) |

`pending_flag` sayesinde aynı kişiye aynı projede aynı anda yalnızca **bir bekleyen** davet olabilir (MySQL'de kısmi unique indeks olmadığı için üretilmiş kolon hilesi; `NULL` değerler unique kontrolüne girmez, geçmiş kayıtlar engellemez).
İndeks: `(invitee_id, status)` – "bana gelen bekleyen davetler" sorgusu için.
Kabul edildiğinde tek transaction'da: davet `ACCEPTED` olur + `project_members`'a `MEMBER` satırı eklenir.

**outbox_events** (Transactional Outbox; iş verisiyle aynı transaction'da yazılır)
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| aggregate_type | VARCHAR(50) | NOT NULL (örn. `PROJECT`) |
| aggregate_id | BIGINT | NOT NULL (silinen proje id'si; FK yok, proje artık yok) |
| event_type | TINYINT UNSIGNED | NOT NULL (0=`PROJECT_DELETED`; yeni olaylar sona eklenir) |
| payload | JSON | NULL (ek bilgi gerekirse) |
| status | TINYINT UNSIGNED | NOT NULL, default 0 (0=`PENDING`, 1=`SENT`, 2=`FAILED`) |
| attempts | INT | NOT NULL, default 0 |
| next_attempt_at | DATETIME(6) | NOT NULL |
| last_error | VARCHAR(500) | NULL |
| created_at | DATETIME(6) | NOT NULL |
| processed_at | DATETIME(6) | NULL |

İndeks: `(status, next_attempt_at)` – relay sorgusu için. İşleyiş: bkz. 04 "Proje Kalıcı Silme – Outbox".

## task_db
**tasks**
| Kolon | Tip | Kısıt |
|---|---|---|
| id | BIGINT | PK |
| project_id | BIGINT | NOT NULL (mantıksal) |
| title | VARCHAR(200) | NOT NULL |
| description | TEXT | NULL |
| status | TINYINT UNSIGNED | NOT NULL, default 0 (0=`TODO`, 1=`IN_PROGRESS`, 2=`DONE`) |
| priority | TINYINT UNSIGNED | NOT NULL, default 1 (0=`LOW`, 1=`MEDIUM`, 2=`HIGH`) |
| assignee_id | BIGINT | NULL |
| reporter_id | BIGINT | NOT NULL |
| due_date | DATE | NULL |
| position | INT | NOT NULL (kolon içi sıra, 0'dan başlar) |
| created_at / updated_at | DATETIME(6) | NOT NULL |

İndeksler: `(project_id, status, position)`, `(assignee_id, status)`, `(due_date)`, `(project_id, priority)`.
Arama (`q`) ilk sürümde `LIKE %q%`; veri büyürse FULLTEXT indeks (`title`, `description`) eklenir.

## Sıralama (position) Stratejisi
`move` isteğinde hedef kolon ve hedef index gelir. Hedef kolondaki `position >= index` olan kayıtlar +1 kaydırılır, görev yeni yerine yazılır; kaynak kolon boşluğu kapatılır. İşlem tek transaction'da, proje bazlı kilit/optimistic lock (`@Version`) ile yapılır.

## Silme / Bütünlük Kuralları
- Kullanıcı silinmez, `enabled=false` yapılır (görev/proje referansları bozulmaz).
- Proje arşivlenir; kalıcı silmede proje silme + `outbox_events` kaydı tek transaction'da yazılır, görevleri relay asenkron ve yeniden denemeli olarak temizler (en az bir kez teslim; task-service ucu idempotent).
- Atanan kullanıcı projeden çıkarılırsa task-service'teki `assignee_id` null'lanır (dahili uç).

## Başlangıç Verisi
`auth_db` için tek bir varsayılan `ADMIN` kullanıcı (şifresi env ile verilir, repo'ya yazılmaz) migration yerine uygulama başlangıcında oluşturulur.
