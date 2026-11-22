# syntax=docker/dockerfile:1

# ---- Build: compile and package the jar. Tests run in CI (and with ./mvnw verify), not while building the image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Download dependencies first so this layer is cached until the pom changes.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/chapelbook-backend-*.jar /workspace/app.jar

# ---- Run: a JRE only, as an unprivileged user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S chapelbook && adduser -S -G chapelbook chapelbook
WORKDIR /app
COPY --from=build --chown=chapelbook:chapelbook /workspace/app.jar app.jar
USER chapelbook
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
# Size the heap from the container's memory limit rather than the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
