# 08 – Frontend (Angular 13)

Ortam: Node 14.18.1, npm 6.14.15, Angular CLI 13. Stil: SCSS + Bootstrap 5. "MPA" yorumu: çok sayfalı yapı, her ana özellik **lazy-loaded modül** ve kendi route'u (bkz. 00-README).

## Klasör Yapısı
```
frontend/src/app/
├─ core/            (tek sefer yüklenen)
│  ├─ auth/         AuthService (access token bellekte), AuthGuard, RoleGuard, `APP_INITIALIZER` ile sessiz refresh
│  ├─ interceptors/ AuthInterceptor (Bearer ekler, 401'de refresh), ErrorInterceptor
│  ├─ models/       arayüzler (User, Project, Task, Page<T> ...)
│  └─ services/     ApiService, ToastService
├─ shared/          ortak bileşenler, pipe'lar (status badge, priority badge, avatar), SharedModule
├─ features/
│  ├─ auth/         login, register
│  ├─ dashboard/    dashboard sayfası
│  ├─ projects/     liste, detay, form, üye yönetimi
│  ├─ tasks/        kanban board, liste görünümü, görev detay/form modal
│  └─ users/        (ADMIN) kullanıcı yönetimi, profil
├─ layout/          navbar, sidebar, ana layout
└─ environments/    apiUrl = /api (relative; yerelde proxy.conf.json, Docker'da nginx → gateway)
```

## Sayfalar / Route'lar
| Route | Modül | Guard |
|---|---|---|
| `/login`, `/register` | auth | – |
| `/dashboard` | dashboard | AuthGuard |
| `/projects` | projects | AuthGuard |
| `/projects/:id` | projects (detay + üyeler) | AuthGuard |
| `/invitations` | projects (Davetlerim: kabul/reddet) | AuthGuard |
| `/projects/:id/board` | tasks (Kanban) | AuthGuard |
| `/projects/:id/tasks` | tasks (liste, arama/filtre) | AuthGuard |
| `/admin/users` | users | AuthGuard + RoleGuard(ADMIN) |
| `/profile` | users | AuthGuard |

## Reaktif Yapı (RxJS)
- Servis tabanlı state: `BehaviorSubject` + `asObservable()`; ağır kütüphane (NgRx) ilk sürümde yok.
- Arama kutusu: `valueChanges.pipe(debounceTime(300), distinctUntilChanged(), switchMap(...))`.
- Filtreler `combineLatest` ile birleşir; URL query param'larıyla senkron (sayfa yenilense de filtre kalır).
- Token yenileme: interceptor'da tek seferlik refresh (`shareReplay`/subject ile kilit), bekleyen istekler yeni token ile tekrar denenir. Refresh isteği gövdesizdir; cookie tarayıcı tarafından eklenir (aynı origin). Refresh 401 dönerse oturum temizlenir, login'e gidilir.
  - Uygulama (2-E): tek seferlik kilit `AuthService.refresh()` içinde (`shareReplay(1)` + `finalize`), hem `APP_INITIALIZER` hem `AuthInterceptor` aynı metodu kullanır. Tekrar deneme **bir kez** yapılır; yeni token'la da 401 gelirse (örn. hesap pasifleştirildi) oturum biter (`sessionExpired()` → `/login?returnUrl=...`).
  - Bearer yalnızca relative `/api/...` isteklerine eklenir (başka adreslere token gitmez); `/api/auth/**` uçlarında eklenmez ve 401'de yenileme denenmez (refresh'in 401'i döngü yaratmasın).
  - Interceptor sırası `AuthInterceptor` → `ErrorInterceptor` (CoreModule). `ErrorInterceptor` yalnızca ağ hatası (0), 5xx ve 403 (kimlik uçları hariç) için toast gösterir; 400/404/409 formlara/sayfalara, 401 AuthInterceptor'a bırakılır. Bir istek için kapatmak: `HttpContext().set(SKIP_ERROR_TOAST, true)`.
  - Hata gövdesi okumak: `extractApiError(err)` → `{error, message, fieldErrors}`.
- Paket bütçesi: başlangıç paketi uyarı eşiği 600 kB (Bootstrap CSS+JS ~266 kB; özellik sayfaları lazy-loaded olduğu için büyümesi beklenmez), hata 1 MB. `legend+* -> Cannot read property 'type'` build notu Bootstrap'in bir kuralının Angular 13 CSS optimize edicisinde atlanmasıdır, zararsız.
- Çıkış: `POST /api/auth/logout` (cookie sunucuda silinir) + bellekteki token/kullanıcı temizlenir.
- Abonelik sızıntısı yok: `async` pipe tercih edilir, aksi halde `takeUntil(destroy$)`.

## Kanban
- `@angular/cdk/drag-drop`: 3 kolon (`cdkDropList`), `cdkDropListConnectedTo` ile bağlı.
- `drop` olayında **iyimser güncelleme**: UI hemen güncellenir, `PATCH /tasks/{id}/move` çağrılır, hata olursa eski duruma geri alınır + toast.
- Kolon başlıkları: Yapılacak / Devam Ediyor / Tamamlandı; kolonda görev sayısı rozeti.
- Kart: başlık, öncelik rozeti, atanan avatarı, bitiş tarihi (gecikmişse kırmızı).
- Üstte arama ve filtre çubuğu (atanan, öncelik); filtre aktifken sürükle-bırak yine çalışır ancak sıra bilgisi yalnızca filtresiz görünümde güncellenir (aksi halde hedef index belirsiz kalır) — veya filtre aktifken sürükleme sadece kolon değiştirir.

## Dashboard
Üst kartlar (proje sayısı, toplam/açık/geciken görev), görev durum dağılımı (doughnut), proje ilerleme çubukları, yaklaşan bitiş tarihleri listesi, bana atanan görevler. `ng2-charts` + Chart.js.

## Responsive
- Bootstrap grid (`col-12 col-md-6 col-lg-4`), navbar mobilde offcanvas/collapse.
- Kanban: masaüstünde 3 kolon yan yana; mobilde yatay kaydırma (scroll-snap) ya da kolonlar alt alta. CDK drag-drop dokunmatikte çalışır.
- Tablolar küçük ekranda kart görünümüne düşer.
- SCSS: `_variables.scss`, `_mixins.scss` (breakpoint mixin), bileşen bazlı stiller; Bootstrap değişkenleri override edilir.

## Hata / Yüklenme Durumları
Tüm liste ekranlarında yükleniyor (skeleton/spinner), boş durum ve hata durumu mesajı; formlarda reaktif form doğrulaması (`ReactiveFormsModule`), sunucudan gelen `fieldErrors` alanlara eşlenir.

## Test
Servisler ve interceptor için birim testler; Kanban `drop` mantığı için bileşen testi. E2E ilk sürümde opsiyonel (Cypress).
