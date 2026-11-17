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
| `/login`, `/register` | auth | GuestGuard (girişliyse → `/dashboard`) |
| `/dashboard` | dashboard | AuthGuard |
| `/projects` | projects (liste: arama, Aktif/Arşiv/Tümü, sayfalama) | AuthGuard |
| `/projects/new`, `/projects/:id/edit` | projects (form) | AuthGuard |
| `/projects/:id` | projects (detay, üyeler, davet gönderme, bekleyen davetler) | AuthGuard |
| `/invitations` | invitations (Davetlerim: kabul/reddet; ayrı küçük lazy modül) | AuthGuard |
| `/projects/:id/board` | tasks (Kanban) | AuthGuard |
| `/projects/:id/tasks` | tasks (liste, arama/filtre) | AuthGuard |
| `/admin/users` | users | AuthGuard + RoleGuard(ADMIN) |
| `/profile` | profile | AuthGuard |
| `**` | layout (404) | — |

Uygulama notları (2-F):
- Korumalı modüllerde hem `canLoad` (giriş yoksa lazy kod hiç indirilmez) hem `canActivate`. AuthGuard giriş yoksa `/login?returnUrl=<adres>`'e yönlendirir; login sonrası yalnızca site içi `returnUrl` kabul edilir (`isSafeReturnUrl`: `//`, `http://`, `/login`, `/register` reddedilir → açık yönlendirme koruması).
- RoleGuard rolleri route `data.roles`'tan okur; yetki yoksa toast + `/dashboard`.
- Auth modülü `''` yolunda olduğu için route listesinde diğer yollardan sonra gelir.
- Kayıt otomatik giriş yapmaz; e-posta login'e **router state** ile taşınır (URL'de/geçmişte görünmez) ve alan doldurulmuş gelir.
- `ReactiveFormsModule` `SharedModule`'den dışa aktarılmaz (AppModule de SharedModule'ü import ettiği için form kütüphanesi ana pakete girerdi); form kullanan lazy modül kendisi import eder.
- Formlar: `applyServerErrors(form, apiError)` sunucu `fieldErrors`'ını alanlara `server` hatası olarak yazar (alan değişince kalkar); `<app-field-error>` alanın altında ilk hatayı Türkçe gösterir. Doğrulayıcılar: `trimmedEmail` (baş/son boşluk hata sayılmaz), `maxUtf8Bytes(72)`, `fieldsMatch`.
- Angular 13: tipli formlar (`nonNullable`, `form.controls.x`) ve route `title` yok (v14+); alanlara `form.get('x')` ile erişilir.

Uygulama notları (2-G):
- Giriş gerektiren sayfalar `MainLayoutComponent`'in (navbar + içerik) çocuklarıdır; layout'ta `canActivate`/`canActivateChild: [AuthGuard]`. Lazy modüller guard'lardan önce indirildiği için her çocukta ayrıca `canLoad` var (`/admin/users`'ta `RoleGuard`, yetkisizin tarayıcısına kod inmez).
- Layout çocuklarında `''` yollu lazy modül **olmamalı**: `/login` eşleşmesi denenirken o modülün `canLoad`'u çalışıp girişsiz kullanıcıyı `/login?returnUrl=/login`'e atar. Bu yüzden profil ve ADMIN listesi ayrı modüller (`profile`, `users`).
- `MainLayoutComponent` **OnPush değil**: router-outlet'e yerleşen sayfalar onun görünümü altında kontrol edilir; OnPush olunca OnPush olmayan sayfalar (profil) HTTP yanıtından sonra yeniden çizilmiyordu.
- Navbar: mobil menü Angular state'iyle (gezinince kapanır); kullanıcı menüsü Bootstrap dropdown'ı (`data-bs-toggle`, bundle global). ADMIN bağlantısı yalnızca ADMIN'e görünür. Bağlantı rengi kontrast için %85 beyaz.
- Profil: ad güncellenince `AuthService.updateCurrentUser` ile navbar da güncellenir. Şifre değişince sunucu tüm oturumları kapatır; istemci `auth.logout({ state })` ile çıkış yapıp login'e `LoginNavigationState { email, notice: 'passwordChanged' }` taşır (kayıt sonrası da aynı yapı: `notice: 'registered'`).
- ADMIN listesi: arama ve sayfa URL'de (`?q=...&page=2`, sayfa 1 tabanlı); arama 300 ms debounce + `replaceUrl`, `switchMap` ile eski istek iptal. Kendi satırında işlem yok; pasifleştirme ve ADMIN yapma `confirm` ister. Masaüstünde tablo, mobilde kart listesi.
- Ortak: `UserService` (`core/services`), `AvatarComponent` (baş harfler, `shared`), şifre kuralları `newPasswordValidators()` (`shared/forms/validators`).

Uygulama notları (3-E):
- `ProjectService` (`core/services`) ve `project.model.ts` (`Project`: `myRole`, `canManage`, `memberCount`). `restore()` PUT tam güncelleme olduğu için ad ve açıklamayı da gönderir.
- Proje listesi: URL `?q=&status=ARCHIVED|ALL&page=` (durum yoksa aktif; sayfa 1 tabanlı), arama 300 ms debounce + `replaceUrl`, sayfa başına 12 kart (1/2/3 sütun). Düzenle/Arşivle/Arşivden çıkar yalnızca `canManage` kartlarda; arşivleme `confirm` ister, işlemden sonra liste yeniden yüklenir (proje filtreden çıkabilir).
- Form: oluşturma ve düzenleme aynı bileşen; yetkisiz veya arşivdeki projede form kilitlenir ve nedeni yazılır. Kaydedince listeye döner (3-F'de detay sayfasına dönecek).
- Ortak `PaginationComponent` (`shared`, 0 tabanlı `page`, `pageChange`): kullanıcı listesi de buna geçti.
- Şablonlarda mutlak `routerLink` (`/projects/new`): göreli bağlantılar testteki sahte `ActivatedRoute` ile çözülemiyordu.

Uygulama notları (3-F):
- Proje detayı önce projeyi `get(id, silent=true)` ile ister (erişim yoksa/proje yoksa genel bildirim yerine sayfada açıklama), sonra üyeleri ve — yönetebilene, aktif projede — bekleyen davetleri. Geçersiz id'de istek atılmaz. Her işlemden sonra (başarılı ya da hatalı) sayfa yeniden yüklenir.
- Üye çıkarma, davet ve davet geri çekme yalnızca `canManage` + aktif projede; OWNER satırında çıkar düğmesi yok. Adı alınamayan kişi "Kullanıcı #id" (`displayName`).
- `InvitePanelComponent` (OnPush): en az 3 karakter, 300 ms debounce, `switchMap` ile eski arama iptal; sonuçta "Sen" / "Üye" / "Davet bekliyor" / "Davet et". Pasif kullanıcılar `UserService.search` içinde ayıklanır. Davet isteğini üst bileşen yapar; OnPush girdileri (`memberIds`, `pendingInviteeIds`) her yüklemede yeni dizi.
- Form kaydı ve düzenlemedeki "Vazgeç" detaya döner; liste kartının başlığı detaya bağlantı.

Uygulama notları (3-G):
- `InvitationService` (`core/services`): `mine()`, `accept()`, `reject()` ve navbar rozeti için tek kaynak `pendingCount$` (kabul/red sayıyı hemen azaltır). `refreshPendingCount()` sessizdir (`SKIP_ERROR_TOAST`, hata yutulur, son değer kalır).
- Navbar rozeti: açılışta (`interval(60 sn).pipe(startWith(0))` — `timer(0)` ilk değeri bir sonraki turda verir), her sayfa değişiminde ve sekmeye dönülünce tazelenir; sekme arka plandayken istek atılmaz. Layout kapanınca (çıkış) sayı sıfırlanır. 0 iken rozet görünmez; ekran okuyucu için "bekleyen davet" metni.
- Davetlerim: kabul edilen davet listeden kalkar ve "Projeye git" bağlantısıyla yeşil uyarı olarak kalır; red onay ister (24 saat uyarısı). 409 (davet geri çekilmiş / proje arşivde) → sunucu mesajı + liste tazelenir. Panel (dashboard) bekleyen davet varsa uyarı gösterir (navbar'ın tazelediği sayıyı kullanır, ayrı istek yok).

Uygulama notları (4-F):
- `@angular/cdk` 13.3 (yalnızca lazy `tasks` paketinde, ~72 kB; başlangıç paketi değişmedi). `TaskService` + `task.model.ts` (`Board`, etiketler, `isOverdue`, `localToday` — `toISOString` UTC verdiği için yerel gün ayrı hesaplanır).
- Route: `projects` modülünde çocuksuz `:id` yalnızca tam `/projects/5` ile eşleşir; sondaki `{ path: ':id', loadChildren: TasksModule }` `/projects/5/board`'u alır. `:id` üst route'ta olduğu için pano `route.parent.paramMap` okur. Proje detayında herkese "Kanban panosu" düğmesi.
- Sürükle-bırak `cdkDropListGroup` ile (3 kolon birbirine bağlı); kolonun `cdkDropListData`'sı durumu. Sıra hesabı saf fonksiyonda (`board-moves.ts` `moveTask`: sunucudaki `move` ile aynı kural, kolon boyuna sınırlama, değişmeyen nesneler korunur).
- İyimser güncelleme: board hemen değişir, taşımalar `concatMap` ile **sırayla** gönderilir (hızlı art arda sürüklemede sunucu da aynı sırayı görür). Hata → bekleyen başka taşıma yoksa önceki hâle dönülür; her durumda kuyruk boşalınca board sunucudan yenilenir (gerçek durum sunucuda). Sunucu görevi beklenenden farklı yere koyduysa (arada başkası değiştirdi) yine yenilenir. Taşıma sürerken gelen yenileme yanıtı yok sayılır. 4xx mesajı bildirimde; 0/5xx/403'ü `ErrorInterceptor` gösterir.
- Kart: başlık, öncelik rozeti (Bootstrap 5.2'de `*-subtle` yok → kendi açık renkleri), bitiş tarihi (gecikmişse kırmızı + "gecikti"), atanan avatarı (ad proje üye listesinden; listede yoksa "Kullanıcı #id"). Üye listesi alınamazsa pano yine açılır.
- Arşivde sürükleme kapalı + uyarı. Erişim yok / proje yok sayfada açıklanır.
- Responsive: masaüstünde 3 eşit kolon; dar ekranda kolonlar %85 genişlikte yatay kayar (`scroll-snap-type: x proximity` — `mandatory`, CDK'nın sürüklerken yaptığı otomatik kaydırmayı geri yapıştırabilir), `cdkScrollable` ile sürüklerken pano otomatik kayar. Dokunmatikte sürükleme 250 ms basılı tutunca başlar (kaydırmayla karışmasın).
- Erişilebilirlik notu: sürükle-bırak fare/dokunmatik içindir; klavyeyle durum değiştirme 4-G'deki görev modalında.

Uygulama notları (4-G):
- Görev modalı (`TaskModalComponent`): Bootstrap modal görünümü Angular ile çizilir (Bootstrap JS/ng-bootstrap yok); odak `cdkTrapFocus` ile modalda, açılışta başlık alanında, kapanınca açan karta döner; Esc ve arka plana tıklama kapatır; `body.modal-open` sayfa kaydırmasını kilitler; mobilde tam ekran. Oluşturma (TODO sonuna), görüntüleme/düzenleme, silme (onaylı; yalnızca `canDelete`). Düzenlemede **durum** seçilebilir: sürükle-bırakın klavye karşılığı (önce PUT, durum değiştiyse `PATCH /status` → yeni kolonun sonu). Atanan seçenekleri proje üyeleri; artık üye olmayan atanan "Kullanıcı #id (üye değil)" olarak korunur. Arşivde salt okunur. 4xx alan hataları alanların altında; 404/409'da pano da yenilenir.
- Kartlar odaklanabilir (`tabindex`, `role=button`); tıklama veya Enter modalı açar. Sürüklemenin sonunda tarayıcının ürettiği tıklama `cdkDragEnded` + bir tur gecikmeyle yok sayılır.
- Filtre çubuğu (istemci tarafı, `board-filter.ts`): arama (başlık+açıklama, Türkçe harf duyarsız), atanan (herkes / bana atanan / atanmamış / kişi), öncelik; kolon rozeti filtreliyken "görünen / toplam". Filtre açıkken sürükleme **yalnızca kolon değiştirir** (görev hedefin sonuna; kolon içi bırakma yok sayılır), görevin gerçek yeri tüm board'da aranır. Sunucu tarafı liste/arama/sayfalama Faz 5'te.
- Proje detayında yalnızca OWNER/ADMIN'e "Projeyi kalıcı sil" bölümü: onay için proje adı yazılır (`prompt`), başarıda `/projects`'e dönülür; görevler sunucuda outbox ile temizlenir. `notBlank` doğrulayıcısı ortak `validators.ts`'e taşındı.

Uygulama notları (5-C) – görev listesi (`/projects/:id/tasks`):
- Sunucu tarafı sıralama ve sayfalama (`TaskService.list`, sayfa başına 20). Sıralama ve sayfa URL'de: `?sort=dueDate,asc&page=2` (sayfa 1 tabanlı; varsayılan `createdAt,desc` ve 1. sayfa yazılmaz). URL ↔ sorgu dönüşümü saf fonksiyonlarda (`task-list-query.ts`); bozuk değerler sessizce varsayılana döner. Yenileme, geri tuşu ve paylaşılan bağlantı aynı listeyi açar; yeni sorgu eski isteği iptal eder (`switchMap`).
- Arayüz tek sütuna göre sıralar (sunucu birden fazlasını destekler). Sütun başlığı düğmedir (`aria-sort`); ilk tıklamada metin/durum/bitiş artan, öncelik/oluşturulma azalan; aynı sütuna tekrar tıklamak yönü çevirir; sıralama değişince 1. sayfaya dönülür.
- Masaüstünde tablo, mobilde (`< md`) kart listesi + "Sırala" seçimi. Yeni sayfa yüklenirken eski sonuç soluk kalır (titreme yok). Boş kalan sayfadan (son görev silindi) son sayfaya geçilir.
- Satır/kart tıklama veya Enter → Kanban'daki `TaskModalComponent`; kayıt/silme sonrası aynı sayfa yeniden yüklenir. Arşivde "Yeni görev" yok, modal salt okunur.
- Proje bağlamı (proje + üyeler) yalnızca proje değişince yüklenir; atanan adı üye listesinden eşlenir.
- Pano ↔ Liste geçişi `TaskViewSwitchComponent` (bağlantı, `aria-current`); proje detayında "Görev listesi" düğmesi. Öncelik/durum rozet stilleri global (`styles.scss`), Kanban ve liste ortak kullanır.

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
- `@angular/cdk/drag-drop`: 3 kolon (`cdkDropList`), `cdkDropListGroup` ile bağlı.
- `drop` olayında **iyimser güncelleme**: UI hemen güncellenir, `PATCH /tasks/{id}/move` çağrılır, hata olursa eski duruma geri alınır + toast.
- Kolon başlıkları: Yapılacak / Devam Ediyor / Tamamlandı; kolonda görev sayısı rozeti.
- Kart: başlık, öncelik rozeti, atanan avatarı, bitiş tarihi (gecikmişse kırmızı).
- Üstte arama ve filtre çubuğu (atanan, öncelik). **Karar (4-G):** filtre aktifken sürükleme yalnızca kolon değiştirir (görev hedef kolonun sonuna eklenir); kolon içi sıralama filtresiz görünümde yapılır (filtreli görünümde hedef index belirsiz).

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
