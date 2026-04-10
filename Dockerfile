# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Scarica le dipendenze prima del sorgente (ottimizza la cache Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/quarkus-app/lib/         ./lib/
COPY --from=build /app/target/quarkus-app/*.jar        ./
COPY --from=build /app/target/quarkus-app/app/         ./app/
COPY --from=build /app/target/quarkus-app/quarkus/     ./quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
