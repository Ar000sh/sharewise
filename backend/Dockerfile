# Backend image. Build from this folder: docker build -t sharewise-backend backend
# The image holds no environment-specific values: the database and the identity provider come from environment
# variables at start (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD,
# SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI); without them the defaults of application.yml apply.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# Dependencies first, so this layer is cached until the pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
# No tests here: the backend job ran them already, and a Testcontainers run does not belong in an image build.
RUN ./mvnw -q -DskipTests package && mv target/*.jar app.jar

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/Ar000sh/sharewise"
RUN useradd --system --no-create-home sharewise
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER sharewise
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
