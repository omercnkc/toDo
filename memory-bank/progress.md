# Progress

## Çalışanlar / Tamamlananlar
- Planlama dokümanları (`docs/00`–`10`): tamam.
- Memory bank çekirdek dosyaları: tamam.

## Yapılacaklar (docs/10-roadmap.md fazları)
- [x] Faz 0 – İskelet (monorepo, Maven parent, common-lib, compose+MySQL, Angular projesi) — 2026-11-12 tamamlandı (mvnw package, ng build, testler, MySQL şema/kullanıcı izolasyonu doğrulandı)
- [ ] Faz 1 – discovery-server + api-gateway
- [ ] Faz 2 – auth-service + frontend login/layout
- [ ] Faz 3 – project-service + proje ekranları + davet akışı
- [ ] Faz 4 – task-service + Kanban + outbox relay (proje kalıcı silme)
- [ ] Faz 5 – Arama/filtre/sıralama
- [ ] Faz 6 – dashboard-service + ekran
- [ ] Faz 7 – Cila, testler, uçtan uca Docker

## Bilinen sorunlar
Yok (kod henüz yok).

## Karar geçmişi
- Gateway+Eureka+Feign (senkron), tek MySQL/3 şema, monorepo, ADMIN/USER + proje üyeleri.
- Task yanıtında yalnızca assigneeId (auth bağımlılığı eklememek için).
- Proje üyeliği davet/onay ile (2026-11-11).
- 2026-11-12: Spring Boot 3.3.x; refresh token httpOnly cookie; proje kalıcı silmede Transactional Outbox; Eureka kalıyor.
