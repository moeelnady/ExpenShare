# ---- STAGE 1: Build the application ----
FROM gradle:8.7-jdk21 AS build

# Set working directory inside image
WORKDIR /home/app

# Copy Gradle build files (for caching dependencies)
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle gradle

# Run dependency download before copying source (for better caching)
RUN gradle dependencies --no-daemon || true

# Copy entire project
COPY . .

# Build the fat jar (shadow jar)
RUN gradle shadowJar --no-daemon

# ---- STAGE 2: Create lightweight runtime image ----
FROM eclipse-temurin:21-jre

# Set work directory
WORKDIR /app

# Copy fat jar from Stage 1
COPY --from=build /home/app/build/libs/*-all.jar app.jar

# Expose application port
EXPOSE 8082

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
