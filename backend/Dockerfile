FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY virtual-lover-common/pom.xml virtual-lover-common/
COPY virtual-lover-dao/pom.xml virtual-lover-dao/
COPY virtual-lover-storage/pom.xml virtual-lover-storage/
COPY virtual-lover-security/pom.xml virtual-lover-security/
COPY virtual-lover-ai/pom.xml virtual-lover-ai/
COPY virtual-lover-service/pom.xml virtual-lover-service/
COPY virtual-lover-web/pom.xml virtual-lover-web/
COPY virtual-lover-app/pom.xml virtual-lover-app/
COPY settings-docker.xml .
RUN mvn -B -s settings-docker.xml dependency:go-offline
COPY . .
RUN mvn -B -s settings-docker.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/virtual-lover-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
