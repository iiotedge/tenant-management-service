# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy all sources
COPY . .

# Was: ARG MAVEN_SETTINGS=default-settings.xml + a plain COPY of that
# path - that file never existed anywhere in this repo, so this failed
# every CI run ("not found"). release-tms.yml already passes a real
# settings.xml via a build secret (secrets: | gpr=...) - this just never
# consumed it, unlike Dockerfile.ci's already-correct version of this
# same step. Also: this service's own pom.xml previously pointed its
# <parent> at a local, in-monorepo-only file that a standalone CI
# checkout can't see at all - fixed separately (now points at the real,
# published iiotedge-parent) - without that fix, this would still 401
# even with a working secret.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr \
    bash -c 'set -euo pipefail; \
      if [ -f /run/secrets/gpr ]; then \
        mvn -s /run/secrets/gpr -B clean package -DskipTests; \
      else \
        mvn -B clean package -DskipTests; \
      fi'

# ---

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
