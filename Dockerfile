# syntax=docker/dockerfile:1.7
#
# Runtime-only image - previously did its own in-container Maven build
# (maven:3.9-eclipse-temurin + --mount=type=secret,id=gpr), which doesn't
# actually work: this service's parent POM is the local, in-monorepo
# microservices/pom.xml (resolved via relativePath), but a Docker build
# context scoped to this directory alone can't see that sibling file -
# Maven falls back to fetching it from GitHub Packages instead, which
# 401s regardless of any secret supplied. That's the exact problem every
# other service's Dockerfile on this platform already sidesteps (see
# e.g. auth-service's own Dockerfile comment) - brought in line with the
# same working pattern instead of patching the broken one further.
# Separately, its previous COPY line referenced "tms-service.jar", which
# doesn't match what Maven actually produces (pom.xml's own
# <finalName>${project.artifactId}</finalName> = tenant-management-service) -
# both found while testing this service in a kind cluster.

# ---- Extract stage: split the fat jar into layers for better caching ----
FROM gcr.io/distroless/java21-debian12:nonroot AS builder

WORKDIR /app
COPY target/tenant-management-service.jar /app/app.jar

RUN ["java", "-Djarmode=tools", "-jar", "/app/app.jar", "extract", "--layers", "--launcher", "--destination", "/app/extracted"]

# ---- Runtime stage ----
FROM gcr.io/distroless/java21-debian12:nonroot

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG VERSION=unknown

LABEL org.opencontainers.image.title="tenant-management-service" \
      org.opencontainers.image.description="IoTMining tenant management microservice" \
      org.opencontainers.image.source="https://github.com/iiotedge/tenant-management-service" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.version="${VERSION}"

WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8083

EXPOSE 8083

# No Docker-level HEALTHCHECK: distroless has no shell/curl/wget to run one
# from - /actuator/health is meant to be probed by the orchestrator instead.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
