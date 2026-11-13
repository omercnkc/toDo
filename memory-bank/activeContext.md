# Active Context

## Şu anki odak
Faz 1 (discovery-server + api-gateway) tamamlandı (2026-11-13, yerel commit'ler; push edilmedi). Faz 2 devam ediyor; sıradaki: 2-B (register/login + SecurityFilterChain + hata yönetimi).

Çalışma şekli: fazlar adımlara bölünür, küçük alt görevler 2–3'erli tek adımda; her adım sonunda değişiklik anlatımı + test sonucu + kullanıcı onayıyla commit.
Faz 2 adımları: 2-A (JWT util + auth DB), 2-B (register/login + SecurityFilterChain + hata yönetimi), 2-C (refresh/logout + gateway gerçek JWT doğrulama + Origin kontrolü), 2-D (kullanıcı uçları + ADMIN seed + test tamamlama), 2-E (FE AuthService/APP_INITIALIZER/interceptor'lar), 2-F (routing/guard + login/register), 2-G (layout, profil, ADMIN liste, uçtan uca kontrol).

## Faz 2 ilerleme
- 2-A tamam (2026-11-13): common-lib `JwtService`/`JwtAuthenticationFilter`/`AuthenticatedUser`/`Role`/`CodedEnumConverter`; auth-service JPA+Flyway (`V1__create_users_and_refresh_tokens.sql`), `User`, `RefreshToken`, repository'ler, Eureka client. auth-service'te henüz Spring Security ve endpoint yok (2-B).
- auth-service `spring.config.import` ile repo kökündeki `.env`'i okur (optional; kökten, backend/'den veya modül dizininden çalıştırılabilir). Docker'da env değişkenleri kullanılır.
- Testler Testcontainers MySQL 8 ile (Docker gerekli). Parent POM'da `testcontainers.version=1.21.4` (1.19.8 Docker 29'a bağlanamıyor).
- MySQL CHECK ihlali (3819) Spring JDBC'de `UncategorizedSQLException` olarak geliyor (DataIntegrityViolation değil).

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
