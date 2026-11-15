FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY backend/ ./
RUN sh gradlew --no-daemon bootJar && cp build/libs/*-SNAPSHOT.jar /tmp/tayotayo.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/tayotayo.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
