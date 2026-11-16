# Stage 2: enable after adding the main service application and executable JAR.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ewm-main-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
