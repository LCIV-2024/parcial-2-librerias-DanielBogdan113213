FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Compilar el proyecto
RUN mvn clean package

# Imagen final
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN mkdir -p /data

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]