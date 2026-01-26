# 1. Build Stage: Use a Maven image to build the app
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the Fat Jar, skip tests to save time
RUN mvn clean package -DskipTests

# 2. Run Stage: Use a lighter Java image to run the app
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built jar from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port (Render/Railway will ignore this but it's good practice)
EXPOSE 5000

# The command to start the server
CMD ["java", "-jar", "app.jar"]