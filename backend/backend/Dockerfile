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

# This EXPOSE value does NOT matter to Render, but keep 8080 as standard
EXPOSE 8080

# 💥 IMPORTANT: Force Java to bind to Render's dynamic PORT
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=$PORT"]
