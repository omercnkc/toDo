# 04 – Servisler

## Ortak Paket Yapısı (her iş servisi)
```
com.kafein.todo.<service>
├─ config/        Security, Feign, OpenAPI konfigürasyonları
├─ controller/    REST controller'lar
├─ service/       İş mantığı
├─ repository/    Spring Data JPA
├─ entity/        JPA entity'leri
├─ dto/           Request/Response DTO'lar
├─ mapper/        MapStruct
├─ client/        Feign client'lar (varsa)
├─ exception/     Özel exception'lar + @RestControllerAdvice
└─ spec/          JPA Specification (filtreleme)
```

## common-lib
JWT parse/validate yardımcıları, `JwtAuthenticationFilter`, `CurrentUser` (id, role) çözücü, ortak `ApiError` modeli, ortak sayfalama DTO (`PageResponse<T>`), Feign `AuthHeaderInterceptor`.

## discovery-server
Eureka Server. Başka sorumluluğu yok.

## api-gateway
Spring Cloud Gateway: route'lar, JWT doğrulama filtresi (global), doğrulanan kullanıcının `X-User-Id` / `X-User-Role` header'ları ile iletilmesi, CORS (frontend origin), rate limit (opsiyonel).

## auth-service (`auth_db`)
- Kayıt, giriş, refresh, çıkış (refresh token iptali). Refresh token gövdede değil `refresh_token` httpOnly cookie'sinde verilir/okunur (bkz. 07).
- Kullanıcı yönetimi: liste/arama (ADMIN), detay, güncelleme, pasifleştirme, şifre değiştirme.
- Başka servislerin kullandığı uçlar: `GET /users/{id}`, `POST /users/lookup` (id listesi → özet bilgi).

## project-service (`project_db`)
- Proje CRUD (silme: yumuşak → `ARCHIVED`; kalıcı silme yalnızca ADMIN/sahip).
- Üyelik: üye çıkar/listele. Proje oluşturan otomatik `OWNER` üye olur.
- Davet: üye doğrudan eklenmez; OWNER davet gönderir, davet edilen kişi kabul ederse `MEMBER` olur (bkz. 05, 06). Bildirim/e-posta kapsam dışı olduğu için davet uygulama içinde "Davetlerim" ekranında görünür. Gateway `/api/invitations/**` yolunu da project-service'e yönlendirir.
- Dahili uçlar: `GET /projects/{id}/members/{userId}` (üyelik kontrolü), `GET /projects/accessible?userId=` (kullanıcının proje id'leri).
- Feign: `auth-service` (kullanıcı doğrulama); `task-service` yalnızca outbox relay'i üzerinden (asenkron, kullanıcı isteği içinde değil).
- **Outbox relay**: `@Scheduled` iş `outbox_events` tablosundaki bekleyen kayıtları işler (bkz. "Proje Kalıcı Silme – Outbox").

## task-service (`task_db`)
- Görev CRUD, atama, durum değişimi, `move` (durum + sıra).
- Arama/filtre/sıralama/sayfalama (Specification).
- Her yazma işleminde Feign ile üyelik doğrulaması (`project-service`).
- Proje silinince/arşivlenince görevlerin akıbeti: arşivlemede görevler korunur; kalıcı silmede project-service'in outbox relay'i `DELETE /internal/tasks/by-project/{id}` dahili ucunu çağırır. Uç **idempotent**tir (silinecek görev yoksa da 204).

## dashboard-service (DB yok)
Kullanıcının erişebildiği projeleri ve görev istatistiklerini Feign ile toplar, tek yanıtta döner. Önbellek (Caffeine, 30–60 sn) opsiyoneldir.
İstatistik hesabı için task-service'te toplu uç sağlanır: `GET /tasks/stats?projectIds=1,2,3` (durum bazlı sayılar, geciken sayısı) — böylece tüm görevler çekilmez.

## Proje Kalıcı Silme – Outbox
1. `DELETE /api/projects/{id}?permanent=true` → **tek transaction**: proje satırı silinir (üyeler/davetler `ON DELETE CASCADE`) + `outbox_events`'e `PROJECT_DELETED` kaydı (`PENDING`) yazılır. İstemciye 204 döner; görev temizliği asenkrondur (genelde birkaç saniye).
2. Relay (`fixedDelay` 5 sn) `status=PENDING AND next_attempt_at <= now` kayıtlarından 20'lik parti alır (`SELECT ... FOR UPDATE SKIP LOCKED` → birden fazla instance'ta aynı kayıt iki kez işlenmez).
3. Her kayıt için `TaskClient` ile `DELETE /internal/tasks/by-project/{id}` çağrılır (servis JWT'si ile, bkz. 07).
4. Başarı → `SENT`, `processed_at` doldurulur. Hata → `attempts+1`, `last_error` yazılır, `next_attempt_at = now + min(5 sn × 2^attempts, 10 dk)`. 10 denemeden sonra `FAILED` + ERROR log (ADMIN için ileride elle yeniden deneme ucu).
5. Temizlik işi: 7 günden eski `SENT` kayıtları günlük silinir.

Tutarlılık: silme commit'inden sonra task-service'in üyelik kontrolü (Feign → project-service) 404 alır, yani silinmiş projeye yeni görev eklenemez; commit öncesi eklenmiş görevler relay temizliğiyle silinir. Dashboard yalnızca erişilebilir proje id'leri için istatistik çektiğinden temizlik beklenirken eski görevler görünmez.

## Feign Client Özeti
| Client (sahibi) | Hedef uçlar |
|---|---|
| `AuthClient` (project, dashboard) | `GET /users/{id}`, `POST /users/lookup` |
| `ProjectClient` (task, dashboard) | `GET /projects/{id}/members/{userId}`, `GET /projects/accessible`, `GET /projects/{id}` |
| `TaskClient` (project – yalnızca outbox relay) | `DELETE /internal/tasks/by-project/{id}` |
| `TaskClient` (dashboard) | `GET /tasks/stats`, `GET /tasks/assigned/{userId}` |
