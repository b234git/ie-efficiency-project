# syntax=docker/dockerfile:1.7
# ============================================================================
# IE-Eff — Spring Boot 4.0.3 / Java 17
# Multi-stage build: Maven → JRE. Final image runs as a non-root user.
# ============================================================================

# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies first (POM only) so source changes don't re-pull the world.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

# Now copy sources and build.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests \
    && cp target/management-*.jar /workspace/app.jar

# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /opt/ie-eff

# Non-root user
RUN useradd --system --uid 1001 --home /opt/ie-eff --shell /usr/sbin/nologin ieapp \
    && mkdir -p /opt/ie-eff/uploads \
    && chown -R ieapp:ieapp /opt/ie-eff

COPY --from=build --chown=ieapp:ieapp /workspace/app.jar /opt/ie-eff/app.jar

USER ieapp
EXPOSE 8080 8081

# JVM tuned for ~4 GB container memory limit; override JAVA_TOOL_OPTIONS to change.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

# Healthcheck hits the localhost-only management port.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/opt/ie-eff/app.jar"]
