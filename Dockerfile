FROM node:22-alpine AS web
WORKDIR /web
COPY dashboard/package.json ./
RUN npm install
COPY dashboard/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ragbot-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
