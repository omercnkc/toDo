# Active Context

## Şu anki odak
Faz 3 (proje) başladı; adım tablosu docs/10'da (3-A..3-G). 3-A tamam (commit onayı bekliyor). Sıradaki: 3-B (proje CRUD + proje bazlı yetki).
Faz 3 varsayımları (kullanıcıya sunuldu, itiraz yok): kalıcı silme + outbox Faz 4'te; arşivden geri alma `PUT /projects/{id}` ile `status: ACTIVE`; üye kendi kendine ayrılamaz; dahili uçlar `/internal/projects/...`; davet rozeti sayfa değişiminde + 60 sn'de bir yenilenir.

## project-service (3-A) notları
- Paketler: `project` (Project, ProjectStatus), `member` (ProjectMember, MemberRole), `invitation` (ProjectInvitation, InvitationStatus), `config` (SecurityConfig, ProjectProperties). Enum converter'ları enum içinde `JpaConverter` iç sınıfı.
- Üye/davet → proje `@ManyToOne(LAZY)`; proje tarafında koleksiyon yok. `pending_flag` entity'de eşlenmez (MySQL üretir).
- JWT yalnızca doğrulanır (`JwtService.verifierOnly`, `project.jwt.secret=${JWT_SECRET}`); test profili `application-test.yml` (sabit secret, eureka kapalı).
- Compose'ta `project-service` (8082, port yayınlanmaz). Gateway route'ları (`/api/projects/**`, `/api/invitations/**`) Faz 1'den beri hazırdı.

## Frontend (2-G) notları
- Route yapısı: korumalı sayfalar `MainLayoutComponent` çocukları (`dashboard`, `profile`, `admin/users`), her lazy çocukta `canLoad`. Layout çocuklarında `''` yollu lazy modül kullanma (girişsiz `/login` eşleşmesinde canLoad yanlış yönlendirir). 404 en sonda, layout dışında.
- `MainLayoutComponent` OnPush olmamalı (router-outlet içindeki OnPush olmayan sayfalar yeniden çizilmiyordu — tarayıcı testinde yakalandı).
- `LoginNavigationState` artık `core/models/auth.model.ts`'de: `{ email, notice: 'registered' | 'passwordChanged' }`. `AuthService.logout(extras?)`.
- Tarayıcı testi: scratchpad `e2e-2g.js` (repo kökü argüman; ADMIN bilgisi `.env`'den okunur, ekran görüntüleri aynı klasöre). 98 frontend birim testi.
- Faz 3'te navbar'a "Projeler" ve "Davetlerim" (+ rozet) bağlantıları eklenecek.

## Frontend (2-F) notları
- Tarayıcı akış testi: scratchpad'de `e2e-auth.js` (headless Chrome + DevTools Protocol, `ws` frontend/node_modules'ten; `NODE_PATH=frontend/node_modules node e2e-auth.js`), ekran görüntüsü için `screenshots.js`. Önce compose (discovery, gateway, auth) + `ng serve` açık olmalı. Proje dışı, repoya girmez; 2-G'de genişletilip yeniden kullanılabilir.
- Görsel kontrolde yakalanan: `app-field-error` input-group (flex) içinde yana diziliyordu → host'a `display:block; width:100%`.

## Frontend (2-E) notları
- `core/`: `AuthService` (BehaviorSubject state, token yalnız bellekte, `refresh()` tek seferlik, `initialize()` APP_INITIALIZER için Promise, `sessionExpired()`, `updateCurrentUser()`), `AuthInterceptor`, `ErrorInterceptor`, `ToastService`, modeller (`ApiError`+`extractApiError`, `User`/`UserDetail`, `AuthResponse`, `Page<T>`), `CoreModule` (HttpClientModule + interceptors + APP_INITIALIZER; tekrar import'a karşı koruma). `shared/`: `SharedModule`, `ToastContainerComponent` (app.component'te).
- Test: `npx ng test --watch=false --browsers=ChromeHeadless` (Chrome kurulu; Node 14.18.1 bash PATH'te aktif). Build: `npx ng build`. 33 frontend testi.
- Interceptor tekrar denemesi `next.handle` ile gider, interceptor'dan yeniden geçmez → retry'ın 401'i switchMap içinde yakalanır (HttpContext işareti işe yaramıyordu).
- Bash'te arka planda `ng serve` başlatınca kapatmak için PowerShell'de port 4200'ün sahibini `Stop-Process` ile durdur (geniş `taskkill node` kullanma).

Çalışma şekli: fazlar adımlara bölünür, küçük alt görevler 2–3'erli tek adımda; her adım sonunda değişiklik anlatımı + test sonucu + kullanıcı onayıyla commit.
Faz 2 adımları: 2-A (JWT util + auth DB), 2-B (register/login + SecurityFilterChain + hata yönetimi), 2-C (refresh/logout + gateway gerçek JWT doğrulama + Origin kontrolü), 2-D (kullanıcı uçları + ADMIN seed + test tamamlama), 2-E (FE AuthService/APP_INITIALIZER/interceptor'lar), 2-F (routing/guard + login/register), 2-G (layout, profil, ADMIN liste, uçtan uca kontrol).

## Faz 2 ilerleme
- 2-A tamam (2026-11-13): common-lib `JwtService`/`JwtAuthenticationFilter`/`AuthenticatedUser`/`Role`/`CodedEnumConverter`; auth-service JPA+Flyway (`V1__create_users_and_refresh_tokens.sql`), `User`, `RefreshToken`, repository'ler, Eureka client. auth-service'te henüz Spring Security ve endpoint yok (2-B).
- auth-service `spring.config.import` ile repo kökündeki `.env`'i okur (optional; kökten, backend/'den veya modül dizininden çalıştırılabilir). Docker'da env değişkenleri kullanılır.
- Testler Testcontainers MySQL 8 ile (Docker gerekli). Parent POM'da `testcontainers.version=1.21.4` (1.19.8 Docker 29'a bağlanamıyor).
- MySQL CHECK ihlali (3819) Spring JDBC'de `UncategorizedSQLException` olarak geliyor (DataIntegrityViolation değil).
- 2-B tamam (2026-11-13): common-lib `ApiException`, `GlobalExceptionHandler`, `ApiErrorAuthenticationEntryPoint`/`AccessDeniedHandler`, `CommonWebAutoConfiguration` (AutoConfiguration.imports; yalnızca SERVLET + Spring Security varsa yüklenir). auth-service paket yapısı: `authentication` (controller/service/dto/validation), `user`, `token`, `config`. `POST /auth/register` (201), `POST /auth/login` (access token gövde + refresh cookie). `SecurityConfig`: stateless, csrf kapalı, açık: POST /auth/{register,login,refresh,logout}, actuator health/info. `UserDetailsServiceAutoConfiguration` exclude.
- Test profili: `src/test/resources/application-test.yml` + `@ActiveProfiles("test")` (sabit JWT secret, bcrypt 4, eureka kapalı) — .env'e bağımlı olmasın.
- Windows'ta curl ile Türkçe JSON gönderirken gövdeyi UTF-8 dosyadan (`--data-binary @dosya`) ver; komut satırı argümanı yanlış kodlanıp 400 alıyor.
- Bilinen küçük eksik: servis ApiError.path'i StripPrefix sonrası yolu gösterir (`/auth/register`, `/api/...` değil).
- 2-C tamam (2026-11-13): `POST /auth/refresh` (rotasyon; FOR UPDATE kilidi; ROTATED token'a 10 sn tolerans; aksi halde REUSE_DETECTED → tüm token'lar iptal, `noRollbackFor`), `POST /auth/logout` (idempotent 204). Geçersiz refresh → 401 `INVALID_REFRESH_TOKEN` + cookie silme (AuthController'da yerel @ExceptionHandler). V2 migration: `revoked_at`, `revoke_reason`.
- Gateway: gerçek JWT doğrulama (`JwtService.verifierOnly`, `gateway.security.jwt-secret=${JWT_SECRET}`), geçerliyse `X-User-Id/Role` yazılır; refresh/logout'ta Origin kontrolü (`cookie-auth-paths`, `allowed-origins=${FRONTEND_ORIGIN}`). Gateway de .env'i import ediyor; test profili `application-test.yml`.
- Yabancı Origin'i gateway'de önce CORS katmanı reddediyor (gövdesiz 403); `FRONTEND_ORIGIN` public frontend adresiyle birebir aynı olmalı, yoksa aynı origin POST'ları da 403.
- Compose'a auth-service eklendi (port yayınlanmaz); gateway'e `JWT_SECRET` env'i eklendi.
- 2-D tamam (2026-11-13): `/users/me` GET/PUT, `/users/me/password` (tüm oturumlar FORCED iptal), `/users?q=` (USER: q≥3, aktifler, özet; ADMIN: hepsi, detay; `!` ile LIKE kaçışı), ADMIN `/users/{id}` GET/PUT (kendini değiştiremez; pasifleştirme → FORCED). **Servisler arası uçlar `/internal/users/{id}` ve `/internal/users/lookup`** (dokümandaki /users/{id}, /users/lookup yerine; gerekçe docs/06 notu). `AdminBootstrap` (ADMIN_EMAIL/PASSWORD; compose'a eklendi). V3: revoke_reason 3=FORCED. `ApiException.withField(...)` → fieldErrors. GlobalExceptionHandler `HandlerMethodValidationException` (Spring 6.1 @RequestParam kısıtları) ele alıyor.
- Test profilinde ADMIN: admin@test.local / Admin.Sifre1. Backend toplam 157 test.

## Faz 1'de kurulanlar
- Gateway: route'lar `lb://` + `StripPrefix=1` (servis controller'ları `/api` önekisiz), `/api/invitations/**` → project-service, discovery.locator kapalı, `/internal/**` route yok.
- `AuthenticationFilter` (GlobalFilter, order -100): yol hilesi → 400, `X-User-*` silme, açık yollar `gateway.security.open-paths` (login/register/refresh/logout), Bearer yoksa 401. `TODO(2-C)`: imza doğrulama + `X-User-*` ekleme. Hatalar common-lib `ApiError` ile (`ErrorResponseWriter`).
- Gateway common-lib'e bağımlı: common-lib'e servlet'e özgü kod (JwtAuthenticationFilter) eklenirken servlet/web bağımlılıkları `optional` olmalı, gateway (WebFlux) bozulmasın.
- Docker: tek `backend/Dockerfile` (`SERVICE` arg), compose `x-backend-build` anchor; Eureka paneli `127.0.0.1:8761`, gateway `8080`; healthcheck curl ile `/actuator/health`. Env: `EUREKA_URL`, `EUREKA_HOSTNAME`, `EUREKA_INSTANCE_HOSTNAME`, `FRONTEND_ORIGIN`.
- Windows'ta compose build'i `D:\` (büyük harf) dizinden çalıştır (PowerShell `Set-Location 'D:\kafein\toDo'`).

## Faz 0'da kurulanlar / ortam notları
- Git repo `main`, remote `origin` = https://github.com/omercnkc/toDo. Commit mesajları Türkçe, konvansiyonel önek (feat/build/docs/chore) + açıklama gövdesi.
- Backend: Maven Wrapper (`backend/mvnw`, Maven 3.9.16; makinede global Maven yok). Boot 3.3.13, Cloud 2023.0.6, MapStruct 1.6.3. Paket kökü `com.kafein.todo.<servis>`. Faz 0'da bağımlılıklar minimum (JPA/Flyway/Security/Eureka client ilgili fazlarda eklenecek).
- MySQL init `docker/mysql/init.sh` (env'den şifre; düz .sql env okuyamaz). Yerel `.env` rastgele değerlerle üretildi (gitignore).
- Frontend: Angular 13.3, Node 14.18.1 nvm-windows `installs/v14.18.1` altında (varsayılan Node 20; komutlarda bu dizin PATH başına eklenir, global `nvm use` yapılmaz). Bootstrap 5.2.3 sabit (5.3, Angular 13 CSS optimizer'ında selector uyarıları veriyor). `apiUrl=/api`, `proxy.conf.json` → 8080.

## Son değişiklikler
- `docs/00`–`docs/10` planlama dokümanları yazıldı.
- 2026-11-11: proje davet/onay akışı eklendi (`project_invitations`, "Davetlerim" sayfası, Faz 3).
- 2026-11-12: bekleyen kararlar kapatıldı ve dokümanlara işlendi:
  - Spring Boot 3.3.x (+ Spring Cloud 2023.0.x)
  - Refresh token httpOnly cookie (`Path=/api/auth`, `SameSite=Strict`), access token yalnızca bellekte, frontend relative `/api` (aynı origin)
  - Proje kalıcı silmede Transactional Outbox (`project_db.outbox_events` + `@Scheduled` relay → `DELETE /internal/tasks/by-project/{id}`)
  - Eureka kalıyor

## Bekleyen karar
Yok.

## Tercihler
Kullanıcı Türkçe, samimi dille ("kanka") konuşuyor; dokümanlar Türkçe. Adım adım ilerlemek istiyor.
