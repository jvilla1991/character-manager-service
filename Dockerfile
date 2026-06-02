# ── Build stage ────────────────────────────────────────────────────────────────

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

COPY src src/
RUN ./mvnw package -B -q -DskipTests

# ── Runtime stage ───────────────────────────────────────────────────────────────

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
