# Stage 1: Build the application
# Use the 'eclipse-temurin' variant for Maven, matching Java 21
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final, small image
# Use the official 'eclipse-temurin' JRE image for Java 21
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the .jar file from the 'build' stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]