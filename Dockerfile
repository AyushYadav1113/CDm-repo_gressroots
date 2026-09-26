# ==============================================================================
# Multi-stage Dockerfile for Certificate Deployment Manager (CDM) Core
# ==============================================================================

# Build Stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy POM and download dependencies to leverage Docker cache
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build package
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-jammy
LABEL maintainer="CDM Engineering Team"

# Create non-root system user and group
RUN groupadd -r cdm && useradd -r -g cdm -s /bin/false cdm

WORKDIR /app

# Copy built artifact from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R cdm:cdm /app

# Switch to non-root user
USER cdm

# Expose standard application and management port
EXPOSE 8080

# Configure JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
