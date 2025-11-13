# Stage 1: Build the application
# Use the new 'eclipse-temurin' variant for Maven
FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final, small image
# Use the official 'eclipse-temurin' JRE (Java Runtime) image
FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the .jar file from the 'build' stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]