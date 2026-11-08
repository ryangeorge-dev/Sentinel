FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system sentinel && useradd --system --gid sentinel sentinel
WORKDIR /app
COPY --from=build --chown=sentinel:sentinel /build/target/sentinel-*.jar app.jar
USER sentinel
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
