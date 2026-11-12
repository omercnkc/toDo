# Active Context

## Şu anki odak
Faz 0 (iskelet) tamamlandı ve GitHub'a (omercnkc/toDo, main) pushlandı (2026-11-12). Sıradaki: Faz 1 (discovery-server + api-gateway).

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
