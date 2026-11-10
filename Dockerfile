# Build the jar first:  .\gradlew.bat bootJar
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 appuser
WORKDIR /app
COPY build/libs/usermanagement-service.jar app.jar
USER appuser
EXPOSE 9012
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
