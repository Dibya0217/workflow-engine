FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S flowlab && adduser -S flowlab -G flowlab && apk add --no-cache curl
WORKDIR /app
COPY --from=build --chown=flowlab:flowlab /workspace/target/flowlab-1.0.0.jar app.jar
USER flowlab
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 CMD curl -fsS http://localhost:9091/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
