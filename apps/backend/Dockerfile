# syntax=docker/dockerfile:1.7

FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline || (echo "Dependency download failed!" && exit 1)
COPY src ./src
RUN mvn -B -DskipTests package || (echo "Maven build failed!" && exit 1)

FROM eclipse-temurin:21.0.2_13-jre-alpine AS runtime
WORKDIR /app

COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 5001 5002

ENTRYPOINT ["java", "-jar", "app.jar"]
