# System Patterns

## Mimari
Gateway + Eureka + OpenFeign, senkron iletişim. Monorepo. Database-per-service (tek MySQL container, 3 şema, şemalar arası FK yok).

## Servisler
| Servis | Port | DB |
|---|---|---|
| discovery-server | 8761 | – |
| api-gateway | 8080 | – |
| auth-service | 8081 | auth_db |
| project-service | 8082 | project_db |
| task-service | 8083 | task_db |
| dashboard-service | 8084 | – |
| common-lib | – | – |

## Feign bağımlılıkları (döngü yok: auth ← project ← task ← dashboard)
task→project, project→auth, dashboard→project/task/auth. project→task yalnızca asenkron outbox relay ile (kullanıcı isteği içinde senkron döngü yok).

## Önemli kararlar
- JWT: access 15 dk (HS256, yalnızca frontend belleğinde), refresh 7 gün, DB'de hash'li, rotasyonlu, **httpOnly cookie** (`Path=/api/auth`, `SameSite=Strict`). Gateway doğrular, servisler common-lib ile tekrar doğrular. Frontend relative `/api` → aynı origin (ng proxy / nginx).
- Proje kalıcı silme: **Transactional Outbox** — proje silme + `outbox_events` kaydı tek transaction; `@Scheduled` relay (SKIP LOCKED, üstel geri çekilme, 10 deneme) task-service `DELETE /internal/tasks/by-project/{id}` (idempotent) çağırır; relay servis JWT'si (`role=SERVICE`) kullanır.
- Proje üyeliği davet/onay ile (`project_invitations`); kabulde tek transaction ile üye eklenir.
- Flyway migration + JPA `ddl-auto=validate`.
- Kanban sırası: `tasks.position`, `PATCH /tasks/{id}/move` tek transaction.
- Task yanıtı yalnızca `assigneeId` döner; ad eşlemesini frontend proje üyelerinden yapar.
- `/internal/**` uçları gateway'den route edilmez.
- Frontend: servis tabanlı state (BehaviorSubject), NgRx yok.
