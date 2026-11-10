# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# The tests run in CI (they need Docker for Testcontainers), so they are skipped here
RUN mvn -B -q -DskipTests clean package

# ---- Stage 2: small runtime image, without Maven or the source code ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER appuser
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]