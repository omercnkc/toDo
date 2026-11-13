# syntax=docker/dockerfile:1
# Tüm backend servisleri için ortak imaj. Hangi servisin derleneceği SERVICE argümanıyla seçilir:
#   docker build -f backend/Dockerfile --build-arg SERVICE=api-gateway .
# Build context repo köküdür (docker-compose.yml ile aynı).

# ---- build ----
# Maven sürümü backend/.mvn/wrapper ile aynı (3.9.x)
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

# Parent POM tüm modülleri listelediği için backend'in tamamı kopyalanır
# (eksik modül dizini Maven reactor'ünü bozar). target/ vb. .dockerignore ile dışarıda.
COPY backend/ backend/

# ~/.m2 BuildKit önbelleğinde: bağımlılıklar her build'de yeniden indirilmez.
# Testler burada çalışmaz (yerelde / CI'da `./mvnw verify`).
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$SERVICE" || { echo "SERVICE build argümanı gerekli" >&2; exit 1; }; \
    mvn -f backend/pom.xml -B -q -pl "$SERVICE" -am -DskipTests package \
    && cp "backend/$SERVICE/target/$SERVICE.jar" /workspace/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Root olmayan kullanıcı
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

# Konteyner bellek sınırına göre heap
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
