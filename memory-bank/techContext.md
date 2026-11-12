# Tech Context

## Backend
Java 21, Spring Boot 3.3.x (kesin; Spring Cloud 2023.0.x), Spring Data JPA/Hibernate, Spring Security, JWT (jjwt), Spring Cloud (Eureka, Gateway, OpenFeign), Resilience4j, MySQL 8, Flyway, MapStruct, Lombok, springdoc-openapi, JUnit 5/Mockito/Testcontainers, Maven multi-module, Docker.

## Frontend
Angular 13, Node 14.18.1, npm 6.14.15, TypeScript ~4.6, RxJS 7, Bootstrap 5 + SCSS, @angular/cdk (drag-drop), ng2-charts 3.x + chart.js 3.x.
"MPA" = çok sayfalı yapı (lazy-loaded modüller); gerçek server-rendered MPA değil.

## Kısıtlar
- Node 14 / Angular 13 eski: bağımlılık sürümleri uyumluluğa göre sabitlenir (`npm ci`, lock dosyası).
- Windows 11, PowerShell/Git Bash; Docker Desktop.
- Tüm araçlar ücretsiz/açık kaynak (Eureka dahil).

## Geliştirme kurulumu
Yerelde `docker compose up mysql` + servisler IDE'den + `ng serve` (proxy `/api` → 8080). Tam ortam: `docker compose up --build`. Gizli bilgiler `.env` (gitignore), `.env.example` repoda.
