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
| POST | `/api/auth/register` | Herkese açık | `{email,password,fullName}` → `201` + `{id,email,fullName,role}` (rol: USER; otomatik giriş yok) |
| POST | `/api/auth/login` | Herkese açık | `{email,password}` → `{accessToken,expiresIn,user}` + `Set-Cookie: refresh_token` |
| POST | `/api/auth/refresh` | Herkese açık | Gövde yok; refresh token cookie'den okunur → `{accessToken,expiresIn,user}` + yeni cookie (rotasyon). Cookie yok/geçersiz → 401 |
| POST | `/api/auth/logout` | Cookie sahibi | Refresh token iptali + cookie silinir (`Max-Age=0`) → 204 |
| GET | `/api/users/me` | Giriş yapmış | Profil `{id,email,fullName,role,enabled,createdAt,updatedAt}` |
| PUT | `/api/users/me` | Giriş yapmış | `{fullName}` ad güncelleme → profil |
| PUT | `/api/users/me/password` | Giriş yapmış | `{currentPassword,newPassword}` → `204`. **Tüm oturumlar kapanır** (bu cihaz dahil; istemci login'e yönlenir) |
| GET | `/api/users?q=&page=&size=` | Giriş yapmış* | *USER: üye davet etmek için arama; `q` en az 3 karakter, yalnızca `enabled` kullanıcılar, yalnızca `{id,email,fullName}`. ADMIN: kullanıcı yönetimi listesi; `q` isteğe bağlı, pasifler dahil, detaylı. Ad veya e-postada geçen metin (büyük/küçük harf duyarsız; `% _` joker sayılmaz). Sıralama ad, id. `size` 1–100 (varsayılan 20) |
| GET | `/api/users/{id}` | ADMIN | Detay |
| PUT | `/api/users/{id}` | ADMIN | `{role?, enabled?}` (gönderilmeyen alan değişmez). ADMIN kendini değiştiremez (409). Pasifleştirilen kullanıcının tüm oturumları kapanır |

**Servisler arası (dahili) uçlar** — `/internal/**` gateway'de route edilmez, dışarıdan erişilemez. Çağıran servis kullanıcının token'ını iletir; herhangi bir geçerli token yeterli:
| Metot | Yol (servis içi) | Açıklama |
|---|---|---|
| GET | `/internal/users/{id}` | `{id,email,fullName,enabled}`; yoksa 404 `USER_NOT_FOUND` |
| POST | `/internal/users/lookup` | `{ids:[..]}` (1–200) → özet liste; bulunamayan id'ler sonuçta yer almaz |

Not: Önceki taslakta `GET /users/{id}` ve `POST /users/lookup` "ADMIN / dahili" idi. Dahili çağrılar kullanıcının (çoğunlukla USER) token'ıyla geldiği için bu uçlar USER'a açılmak zorunda kalırdı ve id taramasıyla tüm kullanıcılar listelenebilirdi; bu yüzden ayrıldılar.

Giriş yanıtı:
```json
{ "accessToken": "eyJ...", "expiresIn": 900,
  "user": { "id": 1, "email": "a@b.com", "fullName": "Ali Veli", "role": "USER" } }
```
```
Set-Cookie: refresh_token=9f2c...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800
```
Refresh token hiçbir zaman JSON gövdesinde dönmez.

auth hata kodları (`error` alanı):
| Durum | `error` | Ne zaman |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Geçersiz alan (`fieldErrors`): e-posta biçimi, şifre 8 karakter–72 bayt, ad boş/uzun |
| 400 | `BAD_REQUEST` | Okunamayan JSON |
| 401 | `INVALID_CREDENTIALS` | E-posta veya şifre hatalı (ikisi ayırt edilmez) |
| 401 | `UNAUTHORIZED` | Korumalı uçta token yok/geçersiz (`WWW-Authenticate: Bearer`) |
| 403 | `ACCOUNT_DISABLED` | Şifre doğru ama hesap pasif |
| 409 | `EMAIL_ALREADY_EXISTS` | E-posta kayıtlı (büyük/küçük harf duyarsız); `fieldErrors[email]` |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh cookie yok/geçersiz/iptal/süresi dolmuş (cookie silinir) |
| 400 | `INVALID_CURRENT_PASSWORD` | Şifre değiştirmede mevcut şifre yanlış; `fieldErrors[currentPassword]` |
| 400 | `SAME_PASSWORD` | Yeni şifre mevcutla aynı; `fieldErrors[newPassword]` |
| 403 | `FORBIDDEN` | USER, ADMIN ucunu çağırdı |
| 404 | `USER_NOT_FOUND` | Kullanıcı yok |
| 409 | `SELF_MODIFICATION_NOT_ALLOWED` | ADMIN kendi rolünü/durumunu değiştirmeye çalıştı |

İş kuralı hataları bir form alanına aitse `fieldErrors` da dolu gelir (frontend mesajı alanın altında gösterir).

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

Proje uçları uygulama notları (3-B):
- Proje yanıtı (liste ve detayda aynı): `{id, name, description, status, ownerId, myRole, canManage, memberCount, createdAt, updatedAt}`. `myRole` = `OWNER`/`MEMBER`, üye olmayan ADMIN için `null`; `canManage` = OWNER veya ADMIN (yalnızca arayüz kolaylığı; yetki her istekte sunucuda kontrol edilir).
- Liste: `q` proje adında "içerir" araması (`%`, `_` joker sayılmaz); `status` = `ACTIVE`/`ARCHIVED`, boşsa hepsi; sıralama son güncellenen önce; `size` 1–100.
- Olmayan proje → 404 `PROJECT_NOT_FOUND`; var olup erişimi olmayan → 403 `PROJECT_ACCESS_DENIED`; üye ama OWNER/ADMIN değil → 403 `PROJECT_MANAGE_DENIED`.
- `PUT` tam güncellemedir: `{name, description, status?}`; gönderilmeyen `description` silinir. `status` boşsa değişmez. Arşivlenmiş proje yalnızca aynı istekte `"status": "ACTIVE"` ile geri alınırken düzenlenebilir, aksi halde 409 `PROJECT_ARCHIVED`.
- `DELETE` arşivler ve idempotenttir (zaten arşivdeyse de 204). `?permanent=true` Faz 4'e kadar 400 `PERMANENT_DELETE_NOT_AVAILABLE`.
- Sınırlar: ad 1–150 karakter, açıklama en fazla 5000 karakter (boş açıklama `null` saklanır).

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
