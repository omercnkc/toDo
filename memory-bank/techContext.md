# Tech Context

## Backend
Java 21, Spring Boot 3.5.x (7-C'de 3.3'ten yükseltildi; Spring Cloud 2025.0.x), Spring Data JPA/Hibernate, Spring Security, JWT (jjwt), Spring Cloud (Eureka, Gateway, OpenFeign), MySQL 8, Flyway, Caffeine, Lombok, springdoc-openapi (Resilience4j ve MapStruct planlanmıştı, kullanılmadı; MapStruct 7-F'de POM'dan çıkarıldı), JUnit 5/Mockito/Testcontainers, Maven multi-module, Docker.

## Frontend
Angular 13, Node 14.18.1, npm 6.14.15, TypeScript ~4.6, RxJS 7, Bootstrap 5 + SCSS, @angular/cdk (drag-drop), ng2-charts 3.x + chart.js 3.x.
"MPA" = çok sayfalı yapı (lazy-loaded modüller); gerçek server-rendered MPA değil.

## Kısıtlar
- Node 14 / Angular 13 eski: bağımlılık sürümleri uyumluluğa göre sabitlenir (`npm ci`, lock dosyası).
- Windows 11, PowerShell/Git Bash; Docker Desktop.
- Tüm araçlar ücretsiz/açık kaynak (Eureka dahil).

## Geliştirme kurulumu
Tam ortam: `docker compose up --build` (http://localhost:4200). Frontend geliştirme: compose + `docker compose stop frontend` + `ng serve` (proxy `/api` → 8080). Ayrıntı kökteki README'de. Gizli bilgiler `.env` (gitignore), `.env.example` repoda.
