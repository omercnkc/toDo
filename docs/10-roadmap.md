# 10 – Yol Haritası

Her faz sonunda çalışır, test edilebilir bir ara çıktı hedeflenir.

| Faz | İçerik | Bitiş Kriteri | Durum |
|---|---|---|---|
| **0 – İskelet** | Monorepo, Maven parent + modül iskeletleri, `common-lib`, `docker-compose` (MySQL + init.sh), Maven Wrapper, `.env.example`, Angular projesi (`ng new`, Bootstrap/SCSS kurulumu) | `docker compose up mysql` çalışır; backend `./mvnw package` ve frontend `ng build` derlenir | ✅ 2026-11-12 |
| **1 – Altyapı** | `discovery-server`, `api-gateway` (route'lar, CORS, JWT filtresi iskeleti) | Eureka panelinde gateway görünür; gateway ayakta | ✅ 2026-11-13 |
| **2 – Kimlik** | `auth-service` (register/login/refresh/logout, kullanıcı yönetimi, Flyway), gateway JWT doğrulama, httpOnly refresh cookie; frontend: login/register, AuthInterceptor, `APP_INITIALIZER` sessiz refresh, guard'lar, layout | Tarayıcıdan kayıt → giriş → korumalı sayfa; sayfa yenilenince oturum korunur; `localStorage`'da token yok; token yenileme çalışır; ADMIN kullanıcı listesi | ✅ 2026-11-14 |
| **3 – Proje** | `project-service` (CRUD, üyelik, auth-service Feign); davet/onay akışı; frontend: proje liste/detay/form, üye yönetimi, "Davetlerim" sayfası + navbar'da bekleyen davet rozeti | Proje oluştur/düzenle/arşivle, davet gönder → karşı taraf kabul edince üye olur, reddedince olmaz; üye çıkar; yetkisiz erişim 403 | 🔄 3/7 adım |
| **4 – Görev & Kanban** | `task-service` (CRUD, status, move, board, üyelik doğrulama Feign, `/internal/tasks/by-project`); project-service outbox tablosu + relay; frontend: Kanban + görev modalı + CDK drag-drop (iyimser güncelleme) | Sürükle-bırak sonrası yenileyince durum/sıra korunur; proje kalıcı silinince görevleri temizlenir (task-service kapalıyken silinirse servis açılınca temizlenir) | ⏳ |
| **5 – Arama/Filtre/Sıralama** | Specification tabanlı liste ucu, indeksler; frontend: liste görünümü, arama (debounce), filtreler, sıralama, sayfalama, URL senkronu | Tüm filtre kombinasyonları doğru sonuç verir; büyük veride makul sürede döner | ⏳ |
| **6 – Dashboard** | `task-service` stats ucu, `dashboard-service` (Feign toplama, opsiyonel cache); frontend dashboard + grafikler | Özet sayıları, DB ile tutarlı; ADMIN/USER kapsamı doğru | ⏳ |
| **7 – Cila & Teslim** | Responsive düzeltmeleri, hata/boş durum ekranları, birim + entegrasyon testleri (Testcontainers), Swagger doğrulaması, güvenlik gözden geçirme, tüm sistem Docker ile uçtan uca, README/çalıştırma talimatları | `docker compose up --build` ile tüm akış (giriş → proje → görev → Kanban → dashboard) mobil ve masaüstünde sorunsuz | ⏳ |

Durum: ✅ tamamlandı · 🔄 devam ediyor · ⏳ başlanmadı

## Alt Görevler ve İlerleme
Her faz başlarken adımlara bölünür; küçük alt görevler 2–3'erli tek adımda toplanır. Her adım kendi testleriyle biter, uçtan uca doğrulanır ve ayrı commit olur. Bu bölüm her adımın sonunda güncellenir.

### Faz 0 – İskelet ✅
Tek adımda yapıldı (commit'ler `6606481` → `28525e4`).
- **Repo ve planlama:** monorepo, `.gitignore`/`.editorconfig`/`.gitattributes`, `docs/00–10`, memory-bank.
- **Backend iskeleti:** Maven parent (Boot 3.3.13, Cloud 2023.0.6) + Maven Wrapper, 7 modül iskeleti; `common-lib`'de `ApiError` ve `PageResponse`.
- **Docker:** MySQL 8 compose; `init.sh` üç şemayı ve her biri yalnızca kendi şemasına yetkili üç kullanıcıyı `.env` şifreleriyle oluşturur.
- **Frontend iskeleti:** Angular 13.3 + Bootstrap 5.2.3 + SCSS, `/api` proxy'si, `core/shared/features/layout` klasörleri.

### Faz 1 – Altyapı ✅
| Adım | Alt görevler | Durum | Commit | Kısaca ne yapıldı |
|---|---|---|---|---|
| 1-A | Eureka sunucusu + gateway'in Eureka kaydı | ✅ | `052625d` | discovery-server'a actuator ve geliştirme ayarları (self-preservation kapalı, hızlı eviction); gateway Eureka client oldu (`EUREKA_URL`, sabit hostname). |
| 1-B | Route'lar + CORS + JWT filtre iskeleti | ✅ | `882d6c9` | `lb://` route'ları (`StripPrefix=1`; `/api/invitations` → project-service eklendi), `/internal/**` route edilmez; tek origin CORS; `AuthenticationFilter`: yol hilelerini (`..`, `%2e`, `;`) 400 ile reddeder, sahte `X-User-*` header'larını siler, Bearer yoksa 401. |
| 1-C | Docker + uçtan uca kontrol | ✅ | `b7b5f2d` | Tüm servisler için tek `backend/Dockerfile` (`SERVICE` argümanı), `.dockerignore`, compose'a discovery + gateway (healthcheck'li); Windows `d:`/`D:` bake sorunu README'ye not edildi. |

### Faz 2 – Kimlik ✅
| Adım | Alt görevler | Durum | Commit | Kısaca ne yapıldı |
|---|---|---|---|---|
| 2-A | common-lib JWT + auth-service veritabanı | ✅ | `a775843` | `JwtService` (yalnızca HS256, `exp` zorunlu), `JwtAuthenticationFilter`, `AuthenticatedUser`, `Role`, `CodedEnumConverter`; auth-service'e Flyway `V1` (users, refresh_tokens), entity'ler, repository'ler; Testcontainers ile gerçek MySQL testleri. |
| 2-B | Kayıt/giriş + güvenlik zinciri + hata yönetimi | ✅ | `9a28d28` | `POST /auth/register` ve `/auth/login` (refresh token httpOnly cookie'de, DB'de SHA-256 hash'i); kullanıcı keşfine karşı tek hata mesajı + sahte BCrypt; şifre en az 8 karakter, en fazla 72 bayt; common-lib'de ortak `GlobalExceptionHandler` (servlet servislerine otomatik yüklenir). |
| 2-C | Refresh/logout + gateway gerçek JWT doğrulaması | ✅ | `13d371a` | Refresh rotasyonu, tekrar kullanımda tüm oturumların iptali; iki sekme sorunu için `FOR UPDATE` kilidi + 10 sn tolerans (`V2`); idempotent logout; gateway token'ı doğrulayıp `X-User-Id/Role` ekler, refresh/logout'ta Origin kontrolü; compose'a auth-service. |
| 2-D | Kullanıcı uçları + ADMIN seed + test tamamlama | ✅ | `c3833fd` | `/users/me` (profil, ad, şifre değiştirme → tüm oturumlar kapanır), `/users?q=` (USER: ≥3 karakter + özet; ADMIN: tüm liste), ADMIN rol/aktiflik yönetimi (kendini değiştiremez); servisler arası uçlar `/internal/users/**` altına alındı; `.env`'den ADMIN seed; `V3`. |
| 2-E | Frontend çekirdeği: AuthService + interceptor'lar + bildirimler | ✅ | `119aca2` | Token yalnızca bellekte, `APP_INITIALIZER` ile sessiz refresh, tek seferlik refresh kilidi; `AuthInterceptor` 401'de yenileyip bir kez tekrar dener; `ErrorInterceptor` + `ToastService` yalnızca genel hatalarda bildirim; 33 birim testi, `ng serve` proxy'si üzerinden uçtan uca doğrulandı. |
| 2-F | Routing + guard'lar + login/register sayfaları | ✅ | `17e725d` | Lazy `auth` ve geçici `dashboard` modülleri; `AuthGuard` (canLoad dahil, `returnUrl` ile), `RoleGuard`, `GuestGuard`; login/register formları (sunucu `fieldErrors`'ı alanın altında, 72 bayt kuralı, boşluklu e-posta kabul, `returnUrl`'de açık yönlendirme koruması, kayıttan sonra e-posta router state ile taşınır); ortak `FieldErrorComponent` ve form araçları. 72 birim testi + headless Chrome'da 14 adımlık akış testi (kayıt → giriş → yenileme → çıkış). |
| 2-G | Layout + profil + ADMIN listesi + uçtan uca kontrol | ✅ | `40be04e` | Responsive navbar (mobil menü, kullanıcı menüsünden profil/çıkış, ADMIN'e özel bağlantı) ve 404 sayfası; profil (ad güncellemesi navbar'a yansır, şifre değişince çıkış + bilgi mesajıyla login); ADMIN kullanıcı listesi (URL'ye bağlı arama/sayfalama, rol verme, pasifleştirme; mobilde kart görünümü); `RoleGuard.canLoad`. 98 birim testi + headless Chrome'da 32 adımlık uçtan uca kontrol (faz bitiş kriterleri dahil). |

### Faz 3 – Proje 🔄
| Adım | Alt görevler | Durum | Commit | Kısaca ne yapıldı |
|---|---|---|---|---|
| 3-A | project-service altyapısı + veritabanı | ✅ | `42532e5` | `V1` migration: `projects`, `project_members`, `project_invitations` (CHECK'li durum kodları, `ON DELETE CASCADE`, tek bekleyen davet için `pending_flag` üretilmiş kolonu + unique); `ProjectStatus`/`MemberRole`/`InvitationStatus` kodlu enum'lar, entity'ler (davet yalnızca bir kez yanıtlanır), repository'ler; yalnızca doğrulayan JWT ile güvenlik zinciri; compose'a eklendi (port yayınlanmaz), Eureka kaydı ve gateway yönlendirmesi doğrulandı. 14 Testcontainers testi. `outbox_events` Faz 4'te (V2). |
| 3-B | Proje CRUD + proje bazlı yetki | ✅ | `3f6bcb5` | `POST/GET/PUT/DELETE /projects`: oluşturan aynı transaction'da OWNER olur; liste USER'a üyesi olduklarını, ADMIN'e hepsini verir (`q` ad araması, `status`, sayfalama; üye sayısı ve rol N+1 olmadan toplu sorguyla); `ProjectAccessService` (olmayan → 404, erişimsiz → 403, yönetemeyen → 403); arşivleme idempotent, arşivden geri alma `PUT … status=ACTIVE`, arşivdeyken düzenleme 409. LIKE kaçışı common-lib'e (`LikePatterns`) taşındı. 25 project-service testi; gateway üzerinden doğrulandı. |
| 3-C | Üyelik + auth-service Feign + dahili uçlar | ✅ | _commit bekliyor_ | OpenFeign `AuthClient` (Eureka ile `auth-service`), kullanıcı token'ını ileten interceptor, `UserDirectory` (404 → boş, kesinti/5xx/zaman aşımı → 503, 200'lük parçalama); `GET /projects/{id}/members` (adlar tek toplu çağrıyla; auth-service kapalıyken liste adsız ama çalışır), üye çıkarma (OWNER çıkarılamaz, arşivde değişmez); task-service için `/internal/projects/{id}/members/{userId}` ve `/internal/projects/accessible`. Entegrasyon testleri ortak tabana (`IntegrationTestSupport`) alındı. 39 test; Docker'da gerçek Feign çağrısı ve auth-service kesintisi denendi. |
| 3-D | Davet akışı (backend) | ⏳ | | Davet gönder/listele/iptal (OWNER/ADMIN), "bana gelen davetler", kabul (tek transaction'da üyelik) / red; kurallar: kendini/üyeyi/pasifi davet yok, tek bekleyen davet, redden sonra 24 saat, en fazla 50 bekleyen, arşivde davet yok; her kural için test. |
| 3-E | Frontend: proje listesi + form | ⏳ | | `ProjectService`; `/projects` (arama, durum filtresi, URL senkronu, sayfalama, kart görünümü), oluştur/düzenle formu, arşivleme; navbar'a "Projeler". |
| 3-F | Frontend: proje detayı + üyeler + davet gönderme | ⏳ | | `/projects/:id`: proje bilgisi, üye listesi ve çıkarma, kullanıcı arama (≥3 karakter, debounce) ile davet, bekleyen davetleri iptal; düğmeler role göre (OWNER/ADMIN). |
| 3-G | Davetlerim + navbar rozeti + uçtan uca kontrol | ⏳ | | `/invitations` (kabul/red), navbar'da bekleyen davet sayısı rozeti; faz bitiş kriterlerinin tarayıcıda doğrulanması (davet → kabul/red, üye çıkarma, yetkisiz erişim 403). |

### Faz 4–7
Henüz başlanmadı; her faz başlarken bu bölümde adımlara bölünecek.

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
