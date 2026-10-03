# Using Maven image to build the application
FROM maven:3-eclipse-temurin-21 AS build

# Setting the working directory
WORKDIR /app

# Copying the source code into the container
COPY . .

# Running Maven build to create the application JAR
RUN mvn clean package -DskipTests

# Using a slim JDK 21 image to run the built application
FROM eclipse-temurin:21-jdk

# Set the working directory for the runtime container
WORKDIR /app

# Copy the built JAR file from the build container
COPY --from=build /app/target/*.jar app.jar

# Expose the necessary ports
EXPOSE 8080 8443 5005

# Command to run the application with debug options
#ENTRYPOINT ["java", "-jar", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-Dserver.port=8443", "app.jar"]
ENTRYPOINT ["java", "-jar", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-Dserver.port=8080", "-Dserver.ssl.port=8443", "app.jar"]