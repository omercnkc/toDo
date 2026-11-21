# Progress

## Çalışanlar / Tamamlananlar
- Planlama dokümanları (`docs/00`–`10`): tamam.
- Memory bank çekirdek dosyaları: tamam.

## Fazlar (docs/10-roadmap.md)
- [x] Faz 0 – İskelet — 2026-11-12
- [x] Faz 1 – discovery-server + api-gateway — 2026-11-13
- [x] Faz 2 – auth-service + login/layout — 2026-11-14
- [x] Faz 3 – project-service + davet akışı — 2026-11-14
- [x] Faz 4 – task-service + Kanban + outbox — 2026-11-16
- [x] Faz 5 – Arama/filtre/sıralama — 2026-11-17
- [x] Faz 6 – dashboard-service + panel — 2026-11-17
- [x] Faz 7 – Docker, Swagger, güvenlik, arayüz cilası, README — 2026-11-21 (7-E kalıcı e2e paketi atlandı: kullanıcı uçtan uca testi elle yaptı)

## Bilinen sorunlar
- Gateway kapanırken Eureka'ya hemen `DOWN` bildiriyor ama cancel isteği sunucuya ulaşmıyor; kayıt lease süresiyle (~40 sn) düşüyor. DOWN örneklere istek gitmediği için pratik etkisi yok.
- Windows'ta `docker compose build` küçük sürücü harfli dizinden (`d:\`) çalıştırılınca buildx "failed to execute bake" veriyor; `D:\` ile çalıştır.

## Karar geçmişi
- Gateway+Eureka+Feign (senkron), tek MySQL/3 şema, monorepo, ADMIN/USER + proje üyeleri.
- Task yanıtında yalnızca assigneeId (auth bağımlılığı eklememek için).
- Proje üyeliği davet/onay ile (2026-11-11).
- 2026-11-12: Spring Boot 3.3.x; refresh token httpOnly cookie; proje kalıcı silmede Transactional Outbox; Eureka kalıyor.
