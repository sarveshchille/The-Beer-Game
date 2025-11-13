# Stage 1: Build the application
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Final image
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Render injects $PORT (usually 10000 internally), but expose 8080
EXPOSE 8080

# IMPORTANT: Bind Spring Boot to Render’s port
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=$PORT"]
