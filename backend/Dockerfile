# ==============================================================================
# Multi-Stage Dockerfile for PriceHawk Backend (Spring Boot 3 + Java 21)
# Optimized for free cloud hosting (Render, Koyeb, Railway) with memory limits
# ==============================================================================

# Stage 1: Build the application artifact
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, fast, production JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S pricehawk && adduser -S pricehawk -G pricehawk
USER pricehawk:pricehawk

# Copy packaged JAR from build stage
COPY --from=builder /workspace/target/pricehawk-backend-*.jar app.jar

# Environment defaults (overridden at runtime by cloud provider)
ENV PORT=8080
ENV JAVA_OPTS="-XX:+UseSerialGC -Xmx384m -Xms128m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

EXPOSE 8080

# Run with container-aware memory flags
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
