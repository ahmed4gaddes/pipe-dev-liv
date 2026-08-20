# === Stage 1 : Build avec Maven ===
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

# Copier tous les POM (résolution du reactor multi-module)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY ticket-service/pom.xml ticket-service/pom.xml
COPY pipeline-service/pom.xml pipeline-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY audit-service/pom.xml audit-service/pom.xml

# Copier le source de common-lib (dépendance partagée) + ce service
COPY common-lib/src common-lib/src
COPY pipeline-service/src pipeline-service/src

# Build : Maven gère l'ordre (common-lib → pipeline-service)
RUN mvn package -pl pipeline-service -am -DskipTests -B

# === Stage 2 : Image runtime légère (JRE seul) ===
FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
COPY --from=build /workspace/pipeline-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
