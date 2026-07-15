# syntax=docker/dockerfile:1

# =============================================================================
# STAGE 1: Build
# =============================================================================
# Uses the project's own Maven wrapper (mvnw) rather than a Maven-preinstalled
# base image, so the exact Maven version stays pinned to what's checked into
# .mvn/wrapper/maven-wrapper.properties - consistent with what CI and every
# developer machine already uses, rather than a third version floating around.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Dependency layer, cached separately from source: this COPY set only changes
# when pom.xml or the wrapper itself changes, so `mvnw dependency:go-offline`
# below is skipped on every rebuild that only touches src/ - by far the most
# common case during iterative development.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Source layer: only invalidates the cache above when actual code changes.
COPY src/ src/

# Tests are deliberately NOT run in this stage. This is a build/packaging
# step, not a validation gate - CI (see .github/workflows/ci.yml) already runs
# the full test suite as its own separate stage, against real Testcontainers-
# backed Postgres/Redis, before an image is ever built. Running tests again
# here would need Docker-in-Docker for Testcontainers, which is exactly the
# kind of complexity a build stage should not carry, and would only repeat
# work CI already gates on.
RUN ./mvnw clean package -DskipTests -B

# =============================================================================
# STAGE 2: Runtime
# =============================================================================
# JRE-only, not JDK: this image never compiles or runs build tooling, only
# the already-built jar - matches the alpine convention already used for
# every other service in docker-compose.yml (postgres, redis, rabbitmq).
FROM eclipse-temurin:21-jre-alpine AS runtime

# Runs as a dedicated, non-root, non-login user. A compromised app process
# (e.g. via a future dependency CVE) should not run as root inside its own
# container, even though container boundaries already limit blast radius.
RUN addgroup -S saanjha && adduser -S saanjha -G saanjha

WORKDIR /app

COPY --from=build --chown=saanjha:saanjha /build/target/*.jar app.jar

# wget (via busybox) is already present on alpine - avoids installing curl
# just for a health check and keeping the runtime image minimal.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

USER saanjha

EXPOSE 8080

# -XX:MaxRAMPercentage bounds heap as a fraction of the CONTAINER's memory
# limit (not the host's) - JDK 21 already detects cgroup limits by default,
# this just sets a sane fraction so the JVM leaves headroom for
# thread stacks/metaspace/direct buffers rather than claiming ~all of it as heap.
# server.shutdown=graceful + spring.lifecycle.timeout-per-shutdown-phase
# (see application.yml) mean SIGTERM (what `docker stop` sends) lets in-flight
# requests finish before the JVM exits, rather than dropping them.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
