# Dockerfile.workflow
# Used only in GitHub Actions CI/CD pipeline

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin as builder

WORKDIR /app

COPY . .

# Mount GitHub Packages settings.xml secret and use it
ARG MAVEN_SETTINGS=default-settings.xml
COPY ${MAVEN_SETTINGS} /root/.m2/settings.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /root/.m2/settings.xml -B clean package -DskipTests=${SKIP_TESTS}


# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

ENV TZ=UTC

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
