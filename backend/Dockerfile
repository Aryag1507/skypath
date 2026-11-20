# --- Build stage: compile and package the Spring Boot app with Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first (cached unless pom.xml changes) for faster rebuilds.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Then build the jar. Tests run in CI / locally, so they are skipped here to keep
# image builds fast and deterministic.
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Runtime stage: slim JRE image with just the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
