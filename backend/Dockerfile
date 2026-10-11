# syntax=docker/dockerfile:1

# ---- Build: compile and package with JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI (integration tests need Docker, which is not available inside this build).
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/business-os.jar extract --layers --destination target/extracted

# ---- Runtime: JRE only, non-root ----
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/* \
 && groupadd --system bos && useradd --system --gid bos --home-dir /app bos
WORKDIR /app

# Layers ordered from least to most frequently changing, for small image diffs.
COPY --from=build --chown=bos:bos /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=bos:bos /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=bos:bos /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=bos:bos /workspace/target/extracted/application/ ./

USER bos
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "business-os.jar"]
