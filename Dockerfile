# ── Stage 1: Build frontend ───────────────────────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ── Stage 2: Build backend JAR ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress
COPY backend/src/ src/
COPY --from=frontend-build /app/dist/ src/main/resources/static/
RUN mvn package -DskipTests -B --no-transfer-progress

# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
# wget: Docker HEALTHCHECK için gerekli; --no-install-recommends ile katman küçük tutulur
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
# --start-period=90s: Spring Boot + JPA + LangChain4j init süresine tolerans tanır
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
# -XX:MaxRAMPercentage: container bellek limitinin %75'ini heap'e ver (OOMKill önler)
# -Djava.security.egd: /dev/random entropi bekleme sorununu aşar, startup hızlanır
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
