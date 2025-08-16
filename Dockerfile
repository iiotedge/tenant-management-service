# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build

# Adjust to subdirectory
WORKDIR /workspace/services/tms-service

ARG SKIP_TESTS=true

# Copy shared BOM or parent first (if needed)
COPY pom.xml /workspace/pom.xml
COPY services/tms-service/pom.xml ./pom.xml

# Pre-fetch dependencies
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr \
    bash -lc 'set -euo pipefail; \
      SETTINGS_ARG=""; \
      if [ -f /run/secrets/gpr ]; then \
        echo "[INFO] Using GitHub Packages settings at /run/secrets/gpr"; \
        SETTINGS_ARG="-s /run/secrets/gpr"; \
      else \
        echo "[WARN] No Maven settings secret mounted."; \
      fi; \
      mvn $SETTINGS_ARG -B -U -DskipTests dependency:go-offline'

# Copy sources
COPY services/tms-service/src ./src

# Build app
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr \
    bash -lc 'set -euo pipefail; \
      SETTINGS_ARG=""; \
      if [ -f /run/secrets/gpr ]; then SETTINGS_ARG="-s /run/secrets/gpr"; fi; \
      mvn $SETTINGS_ARG -B -DskipTests=${SKIP_TESTS} clean package'

# Runtime image
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/tms-service/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+AlwaysActAsServerClassMachine -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
