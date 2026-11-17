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
`JwtService` (HS256 üretim/doğrulama; yalnızca HS256 kabul, `exp` zorunlu), `JwtAuthenticationFilter` (servlet; bean değil, güvenlik zincirine `addFilterBefore` ile eklenir), `AuthenticatedUser` (id, email, role — controller'da `@AuthenticationPrincipal` ile alınır; ayrı bir `CurrentUser` çözücüsü yok), `Role` (USER=0, ADMIN=1; hem JWT claim'i hem DB kodu), `CodedEnum` + `CodedEnumConverter` (TINYINT kodlu enum'lar için ortak JPA dönüştürücü), ortak `ApiError` modeli, ortak sayfalama DTO (`PageResponse<T>`), Feign `AuthHeaderInterceptor`.
Servlet, Spring Security ve JPA bağımlılıkları `optional`: common-lib'i kullanan gateway (WebFlux) bunları classpath'ine almaz, yalnızca jjwt gelir.

## discovery-server
Eureka Server. Başka sorumluluğu yok.

## api-gateway
Spring Cloud Gateway: route'lar, JWT doğrulama filtresi (global), doğrulanan kullanıcının `X-User-Id` / `X-User-Role` header'ları ile iletilmesi, CORS (frontend origin), rate limit (opsiyonel).

## auth-service (`auth_db`)
- Kayıt, giriş, refresh, çıkış (refresh token iptali). Refresh token gövdede değil `refresh_token` httpOnly cookie'sinde verilir/okunur (bkz. 07).
- Kullanıcı yönetimi: liste/arama (ADMIN), detay, güncelleme, pasifleştirme, şifre değiştirme.
- Başka servislerin kullandığı uçlar: `GET /internal/users/{id}`, `POST /internal/users/lookup` (id listesi → özet bilgi). `/internal/**` gateway'de route edilmez (bkz. 06).
- İlk açılışta `ADMIN_EMAIL` / `ADMIN_PASSWORD` ile varsayılan ADMIN oluşturulur (kullanıcı varsa dokunulmaz; şifre politikaya uymazsa servis açılmaz).

## project-service (`project_db`)
- Proje CRUD (silme: yumuşak → `ARCHIVED`; kalıcı silme yalnızca ADMIN/sahip).
- Üyelik: üye çıkar/listele. Proje oluşturan otomatik `OWNER` üye olur.
- Davet: üye doğrudan eklenmez; OWNER davet gönderir, davet edilen kişi kabul ederse `MEMBER` olur (bkz. 05, 06). Bildirim/e-posta kapsam dışı olduğu için davet uygulama içinde "Davetlerim" ekranında görünür. Gateway `/api/invitations/**` yolunu da project-service'e yönlendirir.
- Dahili uçlar: `GET /internal/projects/{id}/members/{userId}` (üyelik kontrolü), `GET /internal/projects/accessible?userId=` (kullanıcının proje id'leri), `GET /internal/projects/memberships?userId=` (üyelikler: rol + proje durumu; task-service'in projeler arası listesi, 5-B). `/internal` öneki: gateway route etmez (auth-service'in `/internal/users/**` kararıyla aynı, 2-D).
- Feign: `auth-service` (kullanıcı doğrulama); `task-service` yalnızca outbox relay'i üzerinden (asenkron, kullanıcı isteği içinde değil).
- **Outbox relay**: `@Scheduled` iş `outbox_events` tablosundaki bekleyen kayıtları işler (bkz. "Proje Kalıcı Silme – Outbox").

## task-service (`task_db`)
- Görev CRUD, atama, durum değişimi, `move` (durum + sıra).
- Arama/filtre/sıralama/sayfalama (Specification): `task/query/` — `TaskListParams` (istekteki ham metin) → `TaskFilter.parse` (doğrulama, tüm hatalar `fieldErrors` olarak birlikte) → `TaskSpecifications` (koşullar + sıralama; `NULLS LAST` için sıralama Specification içinde kurulur, `Pageable` sırasız). Parametreler bkz. 06.
- Her yazma işleminde Feign ile üyelik doğrulaması (`project-service`).
- Proje silinince/arşivlenince görevlerin akıbeti: arşivlemede görevler korunur; kalıcı silmede project-service'in outbox relay'i `DELETE /internal/tasks/by-project/{id}` dahili ucunu çağırır. Uç **idempotent**tir (silinecek görev yoksa da 204).

## dashboard-service (DB yok)
Kullanıcının erişebildiği projeleri ve görev istatistiklerini Feign ile toplar, tek yanıtta döner. Önbellek (Caffeine, 30–60 sn) opsiyoneldir.
- Altyapı (6-B): yalnızca doğrulayan JWT ile güvenlik zinciri (tüm uçlar USER/ADMIN; servis token'ı kabul edilmez, dahili ucu yok), Eureka kaydı, `ProjectClient` (`/internal/projects/memberships`) ve `TaskClient` (`/tasks/stats`) — kullanıcının token'ı common-lib interceptor'ıyla iletilir, kapsam/yetki kararı çağrılan serviste kalır. Hata dönüşümü `ProjectDirectory` / `TaskDirectory`'de: ulaşılamıyor, 5xx, zaman aşımı (bağlantı 2 sn, okuma 5 sn) veya beklenmeyen 4xx → 503 `PROJECT_SERVICE_UNAVAILABLE` / `TASK_SERVICE_UNAVAILABLE`; task-service'in 403'ü (`scope=all`, ADMIN değil) → 403 `STATS_SCOPE_FORBIDDEN`. Rol/durum metin olarak çözülür (project-service yeni değer eklerse dashboard bozulmasın), bilinmeyen alanlar yok sayılır.
- Testler gerçek Feign çağrısıyla: sahte project/task-service JDK `HttpServer` ile, adresler Spring Cloud basit keşif istemcisinden (`spring.cloud.discovery.client.simple.instances.*`); token iletimi, JSON çözümü, zaman aşımı ve bağlantı reddi uçtan uca sınanır.
İstatistik hesabı için task-service'te toplu uç sağlanır: `GET /tasks/stats?scope=mine|all` (proje başına durum sayıları, geciken, bana atanan açık görevler; 6-A) — tüm görevler çekilmez, `GROUP BY project_id, status` ile sayılır (100 bin görevli projede gateway üzerinden ~150 ms). Kapsam proje id listesiyle değil, task-service'in kendi yetki kuralıyla (üyelikler / ADMIN için tüm sistem) belirlenir: istemci başkasının projesini istemeye çalışamaz.

## Proje Kalıcı Silme – Outbox
1. `DELETE /api/projects/{id}?permanent=true` → **tek transaction**: proje satırı silinir (üyeler/davetler `ON DELETE CASCADE`) + `outbox_events`'e `PROJECT_DELETED` kaydı (`PENDING`) yazılır. İstemciye 204 döner; görev temizliği asenkrondur (genelde birkaç saniye).
2. Relay (`fixedDelay` 5 sn) `status=PENDING AND next_attempt_at <= now` kayıtlarından 20'lik parti alır (`SELECT ... FOR UPDATE SKIP LOCKED` → birden fazla instance'ta aynı kayıt iki kez işlenmez). Uygulama (Faz 4-E): parti kısa bir transaction'da kilitlenip `next_attempt_at` 5 dk ileri alınarak **sahiplenilir**, çağrılar transaction dışında yapılır (ağ beklenirken DB kilidi tutulmaz; relay çökerse kayıtlar 5 dk sonra yeniden denenir). Dolu parti gelirse beklemeden sonraki partiye geçilir.
3. Her kayıt için `TaskClient` ile `DELETE /internal/tasks/by-project/{id}` çağrılır (servis JWT'si ile, bkz. 07).
4. Başarı → `SENT`, `processed_at` doldurulur. Hata → `attempts+1`, `last_error` yazılır, `next_attempt_at = now + min(5 sn × 2^attempts, 10 dk)`. 10 denemeden sonra `FAILED` + ERROR log (ADMIN için ileride elle yeniden deneme ucu). **İstisna:** task-service'e *ulaşılamıyorsa* (bağlantı reddi/zaman aşımı, Eureka'da örnek yok → 503) deneme sınırı uygulanmaz, 10 dk aralıkla denenmeye devam eder; `FAILED` yalnızca servisin yanıt verip hata döndüğü durumlar içindir. Böylece task-service ne kadar uzun kapalı kalırsa kalsın açılınca temizlik yapılır.
5. Temizlik işi: 7 günden eski `SENT` kayıtları günlük silinir. (03:30; `FAILED` kayıtlar incelenmek üzere kalır.)
6. Üye çıkarma da aynı yolu kullanır: `MEMBER_REMOVED` olayı (payload `{"userId"}`) → `DELETE /internal/tasks/by-project/{id}/assignees/{userId}`. Bilinen sınır: kişi çıkarıldıktan sonra relay çalışmadan önce (normalde birkaç saniye) projeye yeniden katılıp yeni bir görev atanırsa, o atama da boşaltılır.

Tutarlılık: silme commit'inden sonra task-service'in üyelik kontrolü (Feign → project-service) 404 alır, yani silinmiş projeye yeni görev eklenemez; commit öncesi eklenmiş görevler relay temizliğiyle silinir. Dashboard yalnızca erişilebilir proje id'leri için istatistik çektiğinden temizlik beklenirken eski görevler görünmez.

## Feign Client Özeti
| Client (sahibi) | Hedef uçlar |
|---|---|
| `AuthClient` (project, dashboard) | `GET /internal/users/{id}`, `POST /internal/users/lookup` |
| `ProjectClient` (task, dashboard) | `GET /internal/projects/{id}/members/{userId}`, `GET /internal/projects/accessible`, `GET /internal/projects/memberships`, `GET /projects/{id}` |
| `TaskClient` (project – yalnızca outbox relay) | `DELETE /internal/tasks/by-project/{id}` |
| `TaskClient` (dashboard) | `GET /tasks/stats?scope=` (bana atanan/yaklaşan listeler frontend'de `GET /tasks` projeler arası ucuyla, 6-E) |
