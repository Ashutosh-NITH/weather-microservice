
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build files first (better Docker layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Make wrapper executable
RUN chmod +x gradlew

# GitHub Packages credentials (only used during build)
ARG GPR_USER
ARG GPR_TOKEN

ENV GPR_USER=${GPR_USER}
ENV GPR_TOKEN=${GPR_TOKEN}

# Download dependencies first (cached unless build files change)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build application
RUN ./gradlew clean bootJar --no-daemon -x test

# =========================
# Runtime Stage
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN addgroup --system spring && adduser --system --ingroup spring spring

USER spring:spring

# Copy application
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT [
    "java",
    "-XX:+UseContainerSupport",
    "-XX:MaxRAMPercentage=70.0",
    "-Djava.security.egd=file:/dev/./urandom",
    "-jar",
    "/app/app.jar"
]