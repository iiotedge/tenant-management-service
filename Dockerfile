# ─────────────────────────────────────────────
# Dockerfile for GitHub CI/CD + Local Support
# ─────────────────────────────────────────────

# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin AS builder

WORKDIR /app

COPY . .

# Optional build args from GitHub Actions
ARG SKIP_TESTS=false
ARG MAVEN_SETTINGS=/root/.m2/settings.xml

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr,target=/root/.m2/settings.xml \
    mvn -s ${MAVEN_SETTINGS} -B clean package -DskipTests=${SKIP_TESTS}

# ---- Runtime Stage ----
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

COPY --from=builder /app/target/tms-service.jar ./tms-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "tms-service.jar"]
