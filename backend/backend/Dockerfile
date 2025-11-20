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

# 💥 IMPORTANT FIX: Added -Djava.net.preferIPv4Stack=true
# This forces Java to use IPv4, preventing the "SocketTimeout" error when connecting to Gmail on Render
ENTRYPOINT ["sh", "-c", "java -Djava.net.preferIPv4Stack=true -jar app.jar --server.port=$PORT"]