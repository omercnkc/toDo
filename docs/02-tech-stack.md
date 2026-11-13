# 02 – Teknoloji Yığını

## Backend
| Teknoloji | Sürüm / Not | Amaç |
|---|---|---|
| Java | 21 (LTS) | Dil |
| Spring Boot | 3.3.x (en son yama sürümü; parent POM'da sabitlenir) | Çatı |
| Spring Web | | REST API |
| Spring Data JPA / Hibernate | | ORM |
| Spring Security | | Kimlik doğrulama / yetkilendirme |
| JWT (jjwt 0.12.6) | | Access + refresh token |
| Spring Cloud | 2023.0.x (Netflix Eureka, Gateway, OpenFeign) | Discovery, gateway, servisler arası çağrı |
| Resilience4j | | Feign için timeout / circuit breaker |
| MySQL | 8.x | Veritabanı |
| Flyway | | Şema migration |
| MapStruct + Lombok | | DTO eşleme, boilerplate azaltma |
| springdoc-openapi | | Swagger UI (her serviste) |
| JUnit 5, Mockito, Testcontainers 1.21.4 | | Test (Testcontainers sürümü Boot'un getirdiği 1.19.8'den yükseltildi: Docker 29 uyumu) |
| Maven | multi-module | Build |
| Docker / Docker Compose | | Paketleme ve çalıştırma |

## Frontend
| Teknoloji | Sürüm / Not |
|---|---|
| Angular | 13.x (CLI 13) |
| Node.js / npm | 14.18.1 / 6.14.15 |
| TypeScript | ~4.6 (Angular 13 ile gelen) |
| RxJS | 7.x – reaktif akışlar, `switchMap`, `debounceTime`, `BehaviorSubject` |
| Bootstrap | 5.x + SCSS (grid, utility sınıfları) |
| @angular/cdk | 13.x – `drag-drop` (Kanban) |
| ng2-charts + chart.js | Angular 13 uyumlu sürüm (ng2-charts 3.x / chart.js 3.x) |
| Jest veya Karma/Jasmine | Birim test (varsayılan Karma) |

> Not: Node 14 + Angular 13 kombinasyonu eskidir; bağımlılıklar `package-lock` ile sabitlenmeli ve Node 14 uyumsuz paket sürümleri kullanılmamalıdır.

## Seçim Gerekçeleri
- **Eureka + Gateway + Feign**: Spring ekosistemiyle doğal uyum, ilk sürümde operasyonel yük düşük.
- **Transactional Outbox (broker'sız)**: proje kalıcı silindiğinde görev temizliği aynı transaction'da yazılan outbox kaydı + `@Scheduled` relay ile garanti edilir; RabbitMQ/Kafka gerekmez.
- **httpOnly cookie (refresh token)**: JavaScript erişemez, XSS ile çalınamaz.
- **Flyway**: şema değişiklikleri sürümlü ve tekrarlanabilir; JPA `ddl-auto=validate`.
- **MapStruct**: entity ↔ DTO dönüşümünü derleme zamanında üretir.
- **CDK drag-drop**: ek bağımlılıksız, Angular 13 ile uyumlu Kanban desteği.
