# todo-frontend (Angular 13)

Ortam: **Node 14.18.1 / npm 6.14.15** (`.nvmrc`). Ayrıntılar: `../docs/08-frontend.md`.

```bash
npm ci
npm start          # http://localhost:4200, /api -> http://localhost:8080 (proxy.conf.json)
npm run build      # dist/todo-frontend
npm test
```

API adresi her ortamda relative `/api`'dir (aynı origin → httpOnly refresh cookie'si çalışır, CORS gerekmez).

## Klasörler (`src/app`)
| Klasör | İçerik |
|---|---|
| `core/auth/` | AuthService, AuthGuard, RoleGuard, APP_INITIALIZER sessiz refresh (Faz 2) |
| `core/interceptors/` | AuthInterceptor, ErrorInterceptor (Faz 2) |
| `core/models/` | User, Project, Task, Page<T> arayüzleri |
| `core/services/` | ApiService, ToastService |
| `shared/` | Ortak bileşenler, pipe'lar, SharedModule |
| `features/auth/` | login, register (Faz 2) |
| `features/dashboard/` | Dashboard sayfası (Faz 6) |
| `features/projects/` | Proje liste/detay/form, üyeler, davetler (Faz 3) |
| `features/tasks/` | Kanban, liste, görev modalı (Faz 4-5) |
| `features/users/` | ADMIN kullanıcı yönetimi, profil (Faz 2) |
| `layout/` | Navbar, sidebar, ana layout (Faz 2) |

Global stiller: `src/styles.scss` + `src/styles/_variables.scss` (Bootstrap override) ve `_mixins.scss`.
