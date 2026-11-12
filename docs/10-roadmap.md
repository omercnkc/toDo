# 10 – Yol Haritası

Her faz sonunda çalışır, test edilebilir bir ara çıktı hedeflenir.

| Faz | İçerik | Bitiş Kriteri |
|---|---|---|
| **0 – İskelet** | Monorepo, Maven parent + modül iskeletleri, `common-lib`, `docker-compose` (MySQL + init.sh), Maven Wrapper, `.env.example`, Angular projesi (`ng new`, Bootstrap/SCSS kurulumu) | `docker compose up mysql` çalışır; backend `./mvnw package` ve frontend `ng build` derlenir |
| **1 – Altyapı** | `discovery-server`, `api-gateway` (route'lar, CORS, JWT filtresi iskeleti) | Eureka panelinde gateway görünür; gateway ayakta |
| **2 – Kimlik** | `auth-service` (register/login/refresh/logout, kullanıcı yönetimi, Flyway), gateway JWT doğrulama, httpOnly refresh cookie; frontend: login/register, AuthInterceptor, `APP_INITIALIZER` sessiz refresh, guard'lar, layout | Tarayıcıdan kayıt → giriş → korumalı sayfa; sayfa yenilenince oturum korunur; `localStorage`'da token yok; token yenileme çalışır; ADMIN kullanıcı listesi |
| **3 – Proje** | `project-service` (CRUD, üyelik, auth-service Feign); davet/onay akışı; frontend: proje liste/detay/form, üye yönetimi, "Davetlerim" sayfası + navbar'da bekleyen davet rozeti | Proje oluştur/düzenle/arşivle, davet gönder → karşı taraf kabul edince üye olur, reddedince olmaz; üye çıkar; yetkisiz erişim 403 |
| **4 – Görev & Kanban** | `task-service` (CRUD, status, move, board, üyelik doğrulama Feign, `/internal/tasks/by-project`); project-service outbox tablosu + relay; frontend: Kanban + görev modalı + CDK drag-drop (iyimser güncelleme) | Sürükle-bırak sonrası yenileyince durum/sıra korunur; proje kalıcı silinince görevleri temizlenir (task-service kapalıyken silinirse servis açılınca temizlenir) |
| **5 – Arama/Filtre/Sıralama** | Specification tabanlı liste ucu, indeksler; frontend: liste görünümü, arama (debounce), filtreler, sıralama, sayfalama, URL senkronu | Tüm filtre kombinasyonları doğru sonuç verir; büyük veride makul sürede döner |
| **6 – Dashboard** | `task-service` stats ucu, `dashboard-service` (Feign toplama, opsiyonel cache); frontend dashboard + grafikler | Özet sayıları, DB ile tutarlı; ADMIN/USER kapsamı doğru |
| **7 – Cila & Teslim** | Responsive düzeltmeleri, hata/boş durum ekranları, birim + entegrasyon testleri (Testcontainers), Swagger doğrulaması, güvenlik gözden geçirme, tüm sistem Docker ile uçtan uca, README/çalıştırma talimatları | `docker compose up --build` ile tüm akış (giriş → proje → görev → Kanban → dashboard) mobil ve masaüstünde sorunsuz |

## Bağımlılık Sırası
`0 → 1 → 2 → 3 → 4 → (5, 6 paralel olabilir) → 7`. Frontend ve backend her fazda paralel ilerleyebilir (API sözleşmesi 06'da sabit).

## Test Stratejisi (faz boyunca)
- Servis katmanı: JUnit 5 + Mockito; Feign client'lar için WireMock/mock.
- Repository/entegrasyon: Testcontainers MySQL, Flyway migration'ları ile.
- Controller: `@WebMvcTest` + güvenlik senaryoları (401/403).
- Frontend: servis/interceptor birim testleri, Kanban drop mantığı.
- Manuel uçtan uca kontrol listesi faz 7'de.

## Sonraki Sürüm Adayları
Görev yorumları, dosya ekleri, etiketler, aktivite geçmişi, bildirimler (RabbitMQ ile asenkron), WebSocket ile canlı Kanban, RS256 + JWKS, Config Server, CI/CD.

## Çözülmemiş / Karar Bekleyen Konular
Şu an açık karar yok. Kapatılanlar (2026-11-12):
1. Spring Boot **3.3.x** (+ Spring Cloud 2023.0.x).
2. Task yanıtında yalnızca `assigneeId`; ad eşlemesi frontend'de (bkz. 06).
3. Proje kalıcı silmede görev temizliği: **Transactional Outbox** (bkz. 04, 05).
4. Refresh token: **httpOnly cookie** (bkz. 07).
5. **Eureka kalıyor** (discovery-server, Gateway `lb://` route'ları, Feign servis adlarıyla).
