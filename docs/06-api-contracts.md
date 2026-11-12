# 06 – API Sözleşmeleri

Tüm istekler gateway üzerinden: `http://localhost:8080/api/...`. Gövde JSON. Kimlik: `Authorization: Bearer <accessToken>`.

## Ortak Kurallar
- **Sayfalama**: `?page=0&size=20` → `{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }`
- **Sıralama**: `?sort=dueDate,asc` (birden fazla olabilir)
- **Hata formatı**:
```json
{ "timestamp": "2026-11-10T12:00:00Z", "status": 400, "error": "VALIDATION_ERROR",
  "message": "Doğrulama hatası", "path": "/api/tasks",
  "fieldErrors": [ { "field": "title", "message": "boş olamaz" } ] }
```
- HTTP kodları: 200/201/204, 400 (doğrulama), 401 (kimlik yok/geçersiz), 403 (yetki yok), 404, 409 (çakışma, ör. e-posta kayıtlı).

## auth-service
| Metot | Yol | Yetki | Açıklama |
|---|---|---|---|
| POST | `/api/auth/register` | Herkese açık | Kayıt (rol: USER) |
| POST | `/api/auth/login` | Herkese açık | `{email,password}` → `{accessToken,expiresIn,user}` + `Set-Cookie: refresh_token` |
| POST | `/api/auth/refresh` | Herkese açık | Gövde yok; refresh token cookie'den okunur → `{accessToken,expiresIn,user}` + yeni cookie (rotasyon). Cookie yok/geçersiz → 401 |
| POST | `/api/auth/logout` | Cookie sahibi | Refresh token iptali + cookie silinir (`Max-Age=0`) → 204 |
| GET | `/api/users/me` | Giriş yapmış | Profil |
| PUT | `/api/users/me` | Giriş yapmış | Ad güncelleme |
| PUT | `/api/users/me/password` | Giriş yapmış | `{currentPassword,newPassword}` |
| GET | `/api/users?q=&page=&size=` | Giriş yapmış* | Kullanıcı arama (üye davet etmek için). *USER yalnızca id/ad/e-posta görür; `q` en az 3 karakter, yalnızca `enabled` kullanıcılar döner |
| GET | `/api/users/{id}` | ADMIN / dahili | Detay |
| PUT | `/api/users/{id}` | ADMIN | Rol, enabled güncelleme |
| POST | `/api/users/lookup` | Dahili | `{ids:[..]}` → özet liste |

Giriş yanıtı:
```json
{ "accessToken": "eyJ...", "expiresIn": 900,
  "user": { "id": 1, "email": "a@b.com", "fullName": "Ali Veli", "role": "USER" } }
```
```
Set-Cookie: refresh_token=9f2c...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800
```
Refresh token hiçbir zaman JSON gövdesinde dönmez.

## project-service
| Metot | Yol | Yetki | Açıklama |
|---|---|---|---|
| POST | `/api/projects` | Giriş yapmış | Oluştur (oluşturan OWNER) |
| GET | `/api/projects?q=&status=&page=&size=` | Giriş yapmış | Üyesi olduğum projeler (ADMIN: tümü) |
| GET | `/api/projects/{id}` | Üye / ADMIN | Detay + özet |
| PUT | `/api/projects/{id}` | OWNER / ADMIN | Düzenle |
| DELETE | `/api/projects/{id}` | OWNER / ADMIN | Arşivle (`?permanent=true` kalıcı; 204 döner, görevler outbox ile asenkron temizlenir) |
| GET | `/api/projects/{id}/members` | Üye / ADMIN | Üyeler |
| DELETE | `/api/projects/{id}/members/{userId}` | OWNER / ADMIN | Üye çıkar (OWNER çıkarılamaz) |
| POST | `/api/projects/{id}/invitations` | OWNER / ADMIN | `{userId}` davet gönder (üyelik onaydan sonra oluşur) → 201 |
| GET | `/api/projects/{id}/invitations?status=` | OWNER / ADMIN | Projenin gönderilmiş davetleri |
| DELETE | `/api/projects/{id}/invitations/{invitationId}` | OWNER / ADMIN | Bekleyen daveti iptal et (`CANCELLED`) |
| GET | `/api/invitations?status=PENDING` | Giriş yapmış | Bana gelen davetler |
| POST | `/api/invitations/{id}/accept` | Davet edilen kişi | Kabul → `MEMBER` olarak projeye eklenir |
| POST | `/api/invitations/{id}/reject` | Davet edilen kişi | Reddet |

Davet yanıtı:
```json
{ "id": 7, "status": "PENDING", "createdAt": "2026-11-11T10:00:00Z",
  "project": { "id": 2, "name": "Web Sitesi" },
  "inviter": { "id": 1, "fullName": "Ali Veli" } }
```
Davet kuralları (ihlalde hata kodu):
- Kendini, mevcut üyeyi veya `enabled=false` kullanıcıyı davet edemezsin → 400/409.
- Aynı kişiye aynı projede zaten bekleyen davet varsa → 409.
- Davet reddedilirse aynı kişiye 24 saat boyunca yeniden davet gönderilemez → 409.
- Bir projede en fazla 50 bekleyen davet olabilir → 409.
- Davet edilen dışında kimse kabul/red yapamaz → 403. Zaten yanıtlanmış daveti tekrar yanıtlamak → 409.
- Arşivlenmiş projeye davet gönderilemez/kabul edilemez → 409.

## task-service
| Metot | Yol | Yetki | Açıklama |
|---|---|---|---|
| POST | `/api/tasks` | Proje üyesi | `{projectId,title,description,priority,assigneeId,dueDate}` (durum `TODO`) |
| GET | `/api/tasks` | Proje üyesi | Liste (aşağıdaki filtreler) |
| GET | `/api/tasks/{id}` | Proje üyesi | Detay |
| PUT | `/api/tasks/{id}` | Proje üyesi | Düzenle |
| PATCH | `/api/tasks/{id}/status` | Proje üyesi | `{status}` |
| PATCH | `/api/tasks/{id}/move` | Proje üyesi | `{status, position}` – Kanban sürükle-bırak |
| DELETE | `/api/tasks/{id}` | Reporter / OWNER / ADMIN | Sil |
| GET | `/api/tasks/board?projectId=` | Proje üyesi | `{TODO:[..],IN_PROGRESS:[..],DONE:[..]}` pozisyona göre sıralı |

**Liste filtreleri** (`GET /api/tasks`): `projectId` (zorunlu veya "benim projelerim"), `status`, `priority`, `assigneeId`, `q` (başlık/açıklama), `dueFrom`, `dueTo`, `overdue=true`, `sort` (`dueDate|priority|createdAt|title`), `page`, `size`.

Görev yanıtı:
```json
{ "id": 42, "projectId": 7, "title": "Login ekranı", "description": "...",
  "status": "IN_PROGRESS", "priority": "HIGH", "position": 1,
  "assigneeId": 3, "reporterId": 1,
  "dueDate": "2026-11-20", "createdAt": "...", "updatedAt": "..." }
```
Not: **Karar:** ilk sürümde task yanıtı yalnızca `assigneeId` döner; ad bilgisini frontend proje üye listesinden (`GET /projects/{id}/members`) eşler. Böylece task-service'in auth-service'e bağımlılığı olmaz. Yukarıdaki örnekteki `assignee` nesnesi yerine `"assigneeId": 3` kullanılır.

## dashboard-service
| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/api/dashboard/summary` | Kullanıcıya özel özet |

```json
{ "projectCount": 4, "taskTotals": { "TODO": 12, "IN_PROGRESS": 5, "DONE": 20 },
  "overdueCount": 3, "myOpenTasks": 6,
  "projects": [ { "id": 7, "name": "Web", "total": 18, "done": 9, "progressPercent": 50 } ],
  "upcoming": [ { "id": 42, "title": "Login ekranı", "dueDate": "2026-11-20", "projectId": 7 } ] }
```

## Dahili Uçlar
Servisler arası uçlar (`/internal/**`) gateway'de **yönlendirilmez**; yalnızca ağ içinden ve servis JWT'si/iletilen kullanıcı token'ı ile çağrılır.

| Metot | Yol (sahibi) | Çağıran | Açıklama |
|---|---|---|---|
| DELETE | `/internal/tasks/by-project/{projectId}` (task) | project-service outbox relay | Projenin tüm görevlerini siler; idempotent, her durumda 204 |
