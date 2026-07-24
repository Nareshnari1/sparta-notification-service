# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
# Use a JDK image with Maven to compile the app and produce the runnable jar.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the files needed to resolve dependencies first, so this layer is
# cached and re-downloaded only when pom.xml changes (not on every code edit).
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy the source and build. Skip tests here; run them in CI instead.
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Stage 2: runtime ----------
# A slim JRE image: no compiler, no Maven, smaller and less attack surface.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user for safety.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copy just the built jar from the build stage.
COPY --from=build /app/target/notification-service-*.jar app.jar

# Documents which port the container listens on (matches SERVER_PORT default).
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]