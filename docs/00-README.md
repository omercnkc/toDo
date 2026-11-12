# Görev ve Proje Yönetim Uygulaması

Mikroservis mimarisinde geliştirilecek, proje ve görev yönetimi uygulaması. Bu klasör **kod öncesi planlama** dokümanlarını içerir.

## Okuma Sırası
| # | Doküman | Konu |
|---|---|---|
| 01 | [requirements](01-requirements.md) | Gereksinimler, kullanıcı hikayeleri, kapsam |
| 02 | [tech-stack](02-tech-stack.md) | Teknolojiler ve sürümler |
| 03 | [architecture](03-architecture.md) | Mimari, servis iletişimi, repo yapısı, portlar |
| 04 | [services](04-services.md) | Servis sorumlulukları ve Feign çağrıları |
| 05 | [database](05-database.md) | Şemalar, tablolar, indeksler |
| 06 | [api-contracts](06-api-contracts.md) | Endpoint'ler ve örnek payload'lar |
| 07 | [security](07-security.md) | JWT, roller, yetki matrisi |
| 08 | [frontend](08-frontend.md) | Angular 13 yapısı, Kanban, dashboard |
| 09 | [devops-docker](09-devops-docker.md) | Docker, compose, konfigürasyon |
| 10 | [roadmap](10-roadmap.md) | Fazlar ve bitiş kriterleri |

## Alınan Temel Kararlar
- **Gateway + Eureka + Feign** (senkron iletişim); mesaj kuyruğu (RabbitMQ/Kafka) ilk sürümde yok.
- **Database-per-service**: tek MySQL container, her servise ayrı şema. Şemalar arası FK yok.
- **Monorepo**: backend (Maven multi-module) + frontend (Angular) tek repoda.
- **Roller**: `ADMIN` / `USER`; projelere üye eklenir, görevler üyelere atanır.
- **Spring Boot 3.3.x** (son yama sürümü) + Spring Cloud 2023.0.x, Java 21.
- **Refresh token httpOnly cookie'de**; access token yalnızca bellekte (bkz. 07).
- **Proje kalıcı silmede Transactional Outbox**: görev temizliği project_db'deki `outbox_events` tablosu + zamanlanmış relay ile, broker'sız (bkz. 04, 05).
- **"MPA" yorumu**: Angular tabanlı çok sayfalı yapı (lazy-loaded feature modülleri). Gerçek server-rendered MPA değildir; farklıysa belirtilmeli.

## Açık Noktalar
- Angular 13 + Node 14 eski olduğundan kütüphane sürümleri uyumluluğa göre sabitlenecek (bkz. 02, 08).
