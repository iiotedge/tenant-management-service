# syntax=docker/dockerfile:1.7

############################
#          BUILDER         #
############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ARG SKIP_TESTS=true

COPY pom.xml ./
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr \
    bash -lc 'set -euo pipefail; \
      SETTINGS_ARG=""; \
      if [ -f /run/secrets/gpr ]; then \
        echo "[INFO] Using GitHub Packages settings"; \
        SETTINGS_ARG="-s /run/secrets/gpr"; \
      fi; \
      mvn $SETTINGS_ARG -B -DskipTests=${SKIP_TESTS} clean package'

############################
#          RUNTIME         #
############################
FROM gcr.io/distroless/java21-debian12:nonroot

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.title="tenant-management-service" \
      org.opencontainers.image.description="IoTMining Tenant Management Service" \
      org.opencontainers.image.source="https://github.com/iotmining/tenant-management-service" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+AlwaysActAsServerClassMachine -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
