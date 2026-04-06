# ==============================================================================
# Build stage – compile the fat jar from source
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS build

# git is required by the grgit plugin for version resolution
RUN apk add --no-cache git

# CI=true makes grgit treat the tree as clean even though we only copy a subset
ENV CI=true

WORKDIR /app

# --- layer-cached: Gradle wrapper & build scripts ---
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties analysis.gradle ./
COPY gradle/ gradle/

# Override daemon JVM toolchain to match the JDK available in this image
RUN sed -i 's/toolchainVersion=17/toolchainVersion=21/' gradle/gradle-daemon-jvm.properties

# Pre-download Gradle distribution (cacheable layer)
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# --- source code for all subprojects ---
COPY LavalinkServer/ LavalinkServer/
COPY plugin-api/ plugin-api/
COPY protocol/ protocol/

# .git is required for grgit-based version resolution
COPY .git/ .git/

# Build the musl-optimised fat jar (Alpine uses musl libc)
RUN ./gradlew :Lavalink-Server:bootJar -PtargetPlatform=musl --no-daemon

# ==============================================================================
# Runtime stage – minimal Alpine JRE
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache libgcc

# Run as non-root user (UID/GID 322, matching existing Lavalink Dockerfiles)
RUN addgroup -g 322 -S lavalink && \
    adduser -u 322 -S lavalink lavalink

WORKDIR /opt/Lavalink

RUN chown -R lavalink:lavalink /opt/Lavalink

USER lavalink

COPY --from=build /app/LavalinkServer/build/libs/Lavalink-musl.jar Lavalink.jar

ENTRYPOINT ["java", "-jar", "Lavalink.jar"]
