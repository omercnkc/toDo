# Progress

## Çalışanlar / Tamamlananlar
- Planlama dokümanları (`docs/00`–`10`): tamam.
- Memory bank çekirdek dosyaları: tamam.

## Yapılacaklar (docs/10-roadmap.md fazları)
- [x] Faz 0 – İskelet (monorepo, Maven parent, common-lib, compose+MySQL, Angular projesi) — 2026-11-12 tamamlandı (mvnw package, ng build, testler, MySQL şema/kullanıcı izolasyonu doğrulandı)
- [x] Faz 1 – discovery-server + api-gateway — 2026-11-13 tamamlandı (3 adım: 1-A Eureka, 1-B route/CORS/filtre iskeleti, 1-C Docker; jar ve Docker'da Eureka kaydı + route/401/404/400/CORS doğrulandı)
- [ ] Faz 2 – auth-service + frontend login/layout — devam ediyor (2-A..2-E tamam, 2-F/2-G kaldı; adım ayrıntıları docs/10-roadmap.md "Alt Görevler ve İlerleme")
- [ ] Faz 3 – project-service + proje ekranları + davet akışı
- [ ] Faz 4 – task-service + Kanban + outbox relay (proje kalıcı silme)
- [ ] Faz 5 – Arama/filtre/sıralama
- [ ] Faz 6 – dashboard-service + ekran
- [ ] Faz 7 – Cila, testler, uçtan uca Docker

## Bilinen sorunlar
- Gateway kapanırken Eureka'ya hemen `DOWN` bildiriyor ama cancel isteği sunucuya ulaşmıyor; kayıt lease süresiyle (~40 sn) düşüyor. DOWN örneklere istek gitmediği için pratik etkisi yok.
- Servis örneği yokken gateway'in 503 yanıtı Spring varsayılan formatında (ApiError değil) — Faz 7.
- Windows'ta `docker compose build` küçük sürücü harfli dizinden (`d:\`) çalıştırılınca buildx "failed to execute bake" veriyor; `D:\` ile çalıştır.

## Karar geçmişi
- Gateway+Eureka+Feign (senkron), tek MySQL/3 şema, monorepo, ADMIN/USER + proje üyeleri.
- Task yanıtında yalnızca assigneeId (auth bağımlılığı eklememek için).
- Proje üyeliği davet/onay ile (2026-11-11).
- 2026-11-12: Spring Boot 3.3.x; refresh token httpOnly cookie; proje kalıcı silmede Transactional Outbox; Eureka kalıyor.
