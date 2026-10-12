# syntax=docker/dockerfile:1
# Build context is backend/ (see infra/docker-compose.prod.yml, service "batch") so the whole reactor is
# available for `-am` to resolve. Run-once image: `docker compose run --rm batch --spring.batch.job.name=<job>`
# (infra/batch/bin/run-java-job.sh) — the container exits with the job's status as its exit code.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q -pl batch -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/batch/target/batch.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
