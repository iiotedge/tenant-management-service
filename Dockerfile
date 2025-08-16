# === Stage 1: Build
FROM maven:3.9-eclipse-temurin AS builder

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gpr,target=/root/.m2/settings.xml \
    mvn -s /root/.m2/settings.xml -B clean package -DskipTests

# === Stage 2: Runtime
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
