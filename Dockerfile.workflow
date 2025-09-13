# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy all sources
COPY . .

# Mount GitHub Packages settings.xml secret and use it
ARG MAVEN_SETTINGS=default-settings.xml
COPY ${MAVEN_SETTINGS} /root/.m2/settings.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /root/.m2/settings.xml -B clean package -DskipTests

# ---

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
