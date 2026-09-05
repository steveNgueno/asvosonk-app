# ---------- Etape 1 : build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests -B

# ---------- Etape 2 : execution ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/asvosonk-app-*.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app
USER spring

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
