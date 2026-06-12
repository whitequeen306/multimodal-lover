FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# 依赖层（仅 pom 变更时重建）
COPY pom.xml .
COPY virtual-lover-common/pom.xml virtual-lover-common/pom.xml
COPY virtual-lover-dao/pom.xml virtual-lover-dao/pom.xml
COPY virtual-lover-storage/pom.xml virtual-lover-storage/pom.xml
COPY virtual-lover-security/pom.xml virtual-lover-security/pom.xml
COPY virtual-lover-ai/pom.xml virtual-lover-ai/pom.xml
COPY virtual-lover-service/pom.xml virtual-lover-service/pom.xml
COPY virtual-lover-web/pom.xml virtual-lover-web/pom.xml
COPY virtual-lover-app/pom.xml virtual-lover-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline -q

# 源码层
COPY virtual-lover-common/src virtual-lover-common/src
COPY virtual-lover-dao/src virtual-lover-dao/src
COPY virtual-lover-storage/src virtual-lover-storage/src
COPY virtual-lover-security/src virtual-lover-security/src
COPY virtual-lover-ai/src virtual-lover-ai/src
COPY virtual-lover-service/src virtual-lover-service/src
COPY virtual-lover-web/src virtual-lover-web/src
COPY virtual-lover-app/src virtual-lover-app/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/virtual-lover-app/target/virtual-lover-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
