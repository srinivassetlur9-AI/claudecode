FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 lms
WORKDIR /app
COPY --from=build /app/target/lms-identity-*.jar app.jar
USER lms
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
