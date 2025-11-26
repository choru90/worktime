# Multi-stage build
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN gradle bootJar --no-daemon

# Runtime stage
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
