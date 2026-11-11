FROM eclipse-temurin:21.0.13_9-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21.0.13_9-jre-jammy
RUN groupadd --system app && useradd --system --gid app app && mkdir -p /data/uploads && chown app:app /data/uploads
WORKDIR /app
COPY --from=build /build/target/upoharlab-0.0.1-SNAPSHOT.jar app.jar
USER app
ENV UPLOADS_DIRECTORY=/data/uploads
# Heap follows the container memory limit. Adjust APP_MEMORY_LIMIT for your server.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Duser.timezone=UTC"
EXPOSE 1234
ENTRYPOINT ["java", "-jar", "app.jar"]
