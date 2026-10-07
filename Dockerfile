# -------------------------------------------------------------
# Stage 1: Build Application
# -------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package source code
COPY src ./src
RUN mvn clean package -DskipTests

# -------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a dedicated non-root user for security compliance
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Copy executable jar from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose API port
EXPOSE 8080

# Configure memory limits and container-aware JVM flags
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]