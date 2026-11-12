# 09 – DevOps ve Docker

## Konteynerler
| Servis | İmaj | Not |
|---|---|---|
| mysql | `mysql:8.0` | Volume `mysql_data`, init script `docker/mysql/init.sh` |
| discovery-server | multi-stage (Maven → `eclipse-temurin:21-jre`) | |
| api-gateway | aynı | Dışarı 8080 |
| auth/project/task/dashboard | aynı | Dışarı port açılmaz (geliştirmede debug için açılabilir) |
| frontend | multi-stage (Node 14 build → `nginx:alpine`) | `nginx.conf`: SPA fallback `try_files ... /index.html` + `location /api/ { proxy_pass http://api-gateway:8080; }` (aynı origin → httpOnly cookie sorunsuz) |

## Backend Dockerfile (örnek, servis başına)
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/common-lib backend/common-lib
COPY backend/auth-service backend/auth-service
RUN mvn -f backend/pom.xml -pl auth-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
```

## Frontend Dockerfile (örnek)
```dockerfile
FROM node:14.18.1 AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/todo-frontend /usr/share/nginx/html
```

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
    build: { context: ., dockerfile: backend/discovery-server/Dockerfile }
  api-gateway:
    build: { context: ., dockerfile: backend/api-gateway/Dockerfile }
    ports: ["8080:8080"]
    depends_on: [discovery-server]
  auth-service:
    build: { context: ., dockerfile: backend/auth-service/Dockerfile }
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/auth_db
      SPRING_DATASOURCE_USERNAME: auth_user
      SPRING_DATASOURCE_PASSWORD: ${AUTH_DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      EUREKA_URL: http://discovery-server:8761/eureka
    depends_on:
      mysql: { condition: service_healthy }
      discovery-server: { condition: service_started }
  # project-service, task-service, dashboard-service aynı kalıp
  frontend:
    build: { context: ., dockerfile: frontend/Dockerfile }
    ports: ["4200:80"]
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
- Her serviste `application.yml` (varsayılanlar) + ortam değişkeni override: `SPRING_DATASOURCE_*`, `JWT_SECRET`, `EUREKA_URL`, `SERVER_PORT`, `COOKIE_SECURE` (auth-service; yerel http'de `false`), `OUTBOX_*` (project-service relay aralığı/parti boyutu/maks. deneme; varsayılanlar 5 sn / 20 / 10).
- Profiller: `local` (localhost MySQL/Eureka), `docker` (compose host adları).
- `.env.example` repoda, `.env` gitignore'da.

## Geliştirme Akışı
- Yerelde: yalnızca `docker compose up mysql` + servisler IDE'den + `ng serve` (proxy ile `/api` → 8080, `proxy.conf.json`).
- Tam ortam: `docker compose up --build`.
- Başlatma sırası: mysql → discovery → servisler → gateway; compose `depends_on` + healthcheck, Spring tarafında Eureka/DB yeniden deneme.
- CI (ileri faz): GitHub Actions – `mvn verify`, `npm ci && npm test && npm run build`, imaj build.

## Gözlemlenebilirlik (faz 7, opsiyonel)
Spring Boot Actuator `/actuator/health` (compose healthcheck), yapılandırılmış log, `X-Correlation-Id` header'ının gateway'den servislere taşınması.
