# Stage 1: Build the application
# Use a Maven image to build the .jar file
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app
# Copy just the pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline
# Copy the rest of the source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final, small image
# Use a lightweight Java runtime
FROM openjdk:17-slim
WORKDIR /app
# Copy the .jar file from the 'build' stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
# Expose the port (Render will use $PORT)
EXPOSE 10000
# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]