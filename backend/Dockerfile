# JobLens backend.
#
# Both stages are pinned by tag *and* digest. A tag can be moved; a digest cannot, so this is the
# only way "the image we tested" and "the image that ships" are the same thing.
#
# The runtime is Temurin 25.0.3+9 rather than the 25.0.4+7 used for local development, because
# 25.0.3_9 is the newest Temurin 25 published to Docker Hub. Both are Java 25 LTS. See
# docs/versions.md.

# --- build ------------------------------------------------------------------------------------
FROM eclipse-temurin:25.0.3_9-jdk-noble@sha256:735baf2edc6cd6485240144a84fa4db142b9a6f47b4eb4080f31058d200f9813 AS build

WORKDIR /workspace

# The wrapper and the build definition first: dependency resolution then caches independently of
# source changes, which is most of the build time.
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile ./
RUN ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- runtime ----------------------------------------------------------------------------------
FROM eclipse-temurin:25.0.3_9-jre-noble@sha256:fbcf915c585659b30eb766ada4d6d7cfc9ec1040bf521e95bf61b10a25af73db AS runtime

# A stable, unprivileged identity. Nothing in this image needs to write anywhere but /tmp, and the
# application persists nothing by design.
RUN groupadd --system --gid 10001 joblens \
 && useradd --system --uid 10001 --gid joblens --home /app --shell /usr/sbin/nologin joblens

WORKDIR /app
COPY --from=build --chown=root:root --chmod=444 /workspace/build/libs/*.jar /app/joblens.jar

USER 10001:10001

EXPOSE 8080

# Container-aware defaults. MaxRAMPercentage rather than a fixed heap so the same image behaves
# correctly whatever memory the task definition gives it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# No secrets are baked in. The default provider is the in-process fake, so the image runs with no
# credentials at all; a real provider's key would arrive from the environment at run time.

# No HEALTHCHECK instruction on purpose. The runtime image has no HTTP client, and adding curl to
# get one would widen the image for something the orchestrator does better: ECS and the load
# balancer probe GET /actuator/health over HTTP, which is a real check of the running application.

ENTRYPOINT ["java", "-jar", "/app/joblens.jar"]
