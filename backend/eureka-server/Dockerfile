# syntax=docker/dockerfile:1
# Build context is backend/ (see infra/docker-compose.yml) so the whole reactor
# (this service + common + its siblings) is available for `-am` to resolve.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
# Cache the local repo across builds (BuildKit cache mount, not tied to the image
# layer) so re-running this after a transient Maven Central hiccup — or rebuilding
# any other service — doesn't re-download the whole dependency tree from scratch.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q -pl eureka-server -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/eureka-server.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
