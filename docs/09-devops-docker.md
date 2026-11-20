# 09 – DevOps ve Docker

## Konteynerler
| Servis | İmaj | Not |
|---|---|---|
| mysql | `mysql:8.0` | Volume `mysql_data`, init script `docker/mysql/init.sh` |
| discovery-server | multi-stage (Maven → `eclipse-temurin:21-jre`) | |
| api-gateway | aynı | Dışarı 8080 |
| auth/project/task/dashboard | aynı | Dışarı port açılmaz (geliştirmede debug için açılabilir) |
| frontend | multi-stage (`node:14.18.1-alpine` build → `nginxinc/nginx-unprivileged`) | Dışarı 4200 (konteyner içi 8080, root olmayan nginx). `frontend/nginx.conf`: SPA fallback, `/api/` → `api-gateway:8080` (aynı origin → httpOnly cookie sorunsuz), gzip, önbellek başlıkları |

## Backend Dockerfile (tüm servisler için tek dosya)
`backend/Dockerfile` ortaktır; derlenecek servis `SERVICE` build argümanıyla seçilir (compose'da `x-backend-build` + `args.SERVICE`). Özet:
- Build aşaması `maven:3.9-eclipse-temurin-21`: `backend/` tamamı kopyalanır (parent POM tüm modülleri listelediği için yalnızca ilgili modülleri kopyalamak reactor'ü bozar), `mvn -pl $SERVICE -am -DskipTests package`. `~/.m2` BuildKit cache mount'unda.
- Runtime aşaması `eclipse-temurin:21-jre`, root olmayan `app` kullanıcısı, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.
- Healthcheck compose'da: `curl -fs http://localhost:<port>/actuator/health` (JRE imajında curl var).
- Kök dizindeki `.dockerignore` `target/`, `node_modules`, `.env`, `.git`, `docs` vb. dışarıda bırakır.
- Windows: `docker compose build` "failed to execute bake" verirse terminal dizini büyük sürücü harfiyle (`D:\`) açılmalı (buildx `d:` / `D:` yazımlarını farklı yol sayıyor).

## Frontend Dockerfile (`frontend/Dockerfile`)
Build context repo köküdür (backend ile aynı). Özet:
- Build aşaması `node:14.18.1-alpine` (`package.json` engines ile aynı): önce yalnızca `package*.json` kopyalanır, `npm ci` (npm önbelleği BuildKit cache mount'unda) → kaynak değişince bağımlılık katmanı önbellekten gelir; `ng build --configuration production` → `dist/todo-frontend`.
- Runtime `nginxinc/nginx-unprivileged:1.27-alpine`: root olmayan kullanıcı, 8080'i dinler; compose `4200:8080`. Healthcheck `wget --spider http://127.0.0.1:8080/`.

`frontend/nginx.conf`:
- **SPA:** `try_files $uri $uri/ /index.html` — `/projects/12/board` gibi derin bağlantılar ve yenileme çalışır.
- **`/api/` → gateway:** `proxy_pass` URI'siz verildiği için yol `/api` önekiyle olduğu gibi gider (gateway `StripPrefix=1` yapar). Gateway adı değişkenle + Docker DNS'i (`resolver 127.0.0.11 valid=10s`) ile her istekte çözülür: nginx gateway'den önce açılabilir, gateway yeniden başlayıp IP değiştirse de bağlantı kopmaz. `X-Forwarded-For`/`X-Real-IP` iletilir.
- **Önbellek:** hash'li dosyalar (`main.<hash>.js` vb.) `max-age=31536000, immutable`; bulunamazlarsa 404 (eski sürümün JS'i istenirse `index.html` dönmez). `index.html` ve diğer yollar `no-cache` (her açılışta sunucuya sorulur, yeni sürüm hemen yansır).
- **gzip:** JS/CSS/JSON/SVG, 1 KB üstü.
- Tarayıcı adresi `FRONTEND_ORIGIN` ile aynı olmalı (varsayılan `http://localhost:4200`); refresh/logout istekleri gateway'in Origin kontrolünden geçer.

## docker-compose.yml (iskelet)
```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
      - ./docker/mysql/init.sh:/docker-entrypoint-initdb.d/01-init.sh:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 10
  discovery-server:
    build: { context: ., dockerfile: backend/Dockerfile, args: { SERVICE: discovery-server } }
    environment: { EUREKA_HOSTNAME: discovery-server }
    ports: ["127.0.0.1:8761:8761"]   # Eureka paneli, yalnızca yerel
  api-gateway:
    build: { context: ., dockerfile: backend/Dockerfile, args: { SERVICE: api-gateway } }
    environment:
      EUREKA_URL: http://discovery-server:8761/eureka
      EUREKA_INSTANCE_HOSTNAME: api-gateway
      FRONTEND_ORIGIN: ${FRONTEND_ORIGIN:-http://localhost:4200}
    ports: ["8080:8080"]
    depends_on: { discovery-server: { condition: service_healthy } }
  auth-service:
    build: { context: ., dockerfile: backend/Dockerfile, args: { SERVICE: auth-service } }
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/auth_db
      SPRING_DATASOURCE_USERNAME: auth_user
      SPRING_DATASOURCE_PASSWORD: ${AUTH_DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      EUREKA_URL: http://discovery-server:8761/eureka
    depends_on:
      mysql: { condition: service_healthy }
      discovery-server: { condition: service_started }
  # project-service, task-service, dashboard-service aynı kalıp (dashboard-service DB'siz: yalnızca JWT_SECRET + Eureka, depends_on discovery-server)
  frontend:
    build: { context: ., dockerfile: frontend/Dockerfile }
    ports: ["4200:8080"]   # root olmayan nginx 8080'i dinler
    depends_on: { api-gateway: { condition: service_started } }
volumes:
  mysql_data:
```

## MySQL init (`docker/mysql/init.sh`)
Düz `.sql` dosyası ortam değişkeni okuyamadığı için init bir shell betiğidir; şifreleri `AUTH_DB_PASSWORD`, `PROJECT_DB_PASSWORD`, `TASK_DB_PASSWORD` env'lerinden alıp aşağıdaki SQL'i çalıştırır. Yalnızca boş volume ile ilk açılışta çalışır (şifre değişirse `docker compose down -v`). Üç şema ve her biri için ayrı kullanıcı + yalnızca kendi şemasına yetki:
```sql
CREATE DATABASE auth_db    CHARACTER SET utf8mb4;
CREATE DATABASE project_db CHARACTER SET utf8mb4;
CREATE DATABASE task_db    CHARACTER SET utf8mb4;
CREATE USER 'auth_user'@'%'    IDENTIFIED BY '...'; GRANT ALL ON auth_db.*    TO 'auth_user'@'%';
CREATE USER 'project_user'@'%' IDENTIFIED BY '...'; GRANT ALL ON project_db.* TO 'project_user'@'%';
CREATE USER 'task_user'@'%'    IDENTIFIED BY '...'; GRANT ALL ON task_db.*    TO 'task_user'@'%';
```
(Gerçek betikte `...` yerine `${AUTH_DB_PASSWORD}` vb. gelir.)

## Konfigürasyon
- Her serviste `application.yml` (varsayılanlar) + ortam değişkeni override: `SPRING_DATASOURCE_*`, `JWT_SECRET`, `EUREKA_URL`, `SERVER_PORT`, `COOKIE_SECURE` (auth-service; yerel http'de `false`), `OUTBOX_*` (project-service relay aralığı/parti boyutu/maks. deneme; varsayılanlar 5 sn / 20 / 10), `API_DOCS_ENABLED` (gateway + 4 servis; OpenAPI belgeleri ve Swagger UI, varsayılan `true`).
- Profiller: `local` (localhost MySQL/Eureka), `docker` (compose host adları).
- `.env.example` repoda, `.env` gitignore'da.

## Geliştirme Akışı
- Yerelde: yalnızca `docker compose up mysql` + servisler IDE'den + `ng serve` (proxy ile `/api` → 8080, `proxy.conf.json`).
- Tam ortam: `docker compose up --build`.
- Başlatma sırası: mysql → discovery → servisler → gateway; compose `depends_on` + healthcheck, Spring tarafında Eureka/DB yeniden deneme.
- CI (ileri faz): GitHub Actions – `mvn verify`, `npm ci && npm test && npm run build`, imaj build.

## Gözlemlenebilirlik (faz 7, opsiyonel)
Spring Boot Actuator `/actuator/health` (compose healthcheck), yapılandırılmış log, `X-Correlation-Id` header'ının gateway'den servislere taşınması.
