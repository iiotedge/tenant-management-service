# Build stage
FROM maven:3.9-eclipse-temurin AS builder

WORKDIR /app

COPY . .

# Supports secret-mount (gpr) or default fallback
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr,required=false,target=/run/secrets/gpr \
    bash -c ' \
      SETTINGS_ARG=""; \
      if [ -f /run/secrets/gpr ]; then \
        echo "[INFO] Using custom Maven settings"; \
        SETTINGS_ARG="-s /run/secrets/gpr"; \
      else \
        echo "[WARN] No Maven settings provided, using default"; \
      fi; \
      mvn $SETTINGS_ARG -B clean package -DskipTests \
    '

# Runtime stage
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
