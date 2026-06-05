# syntax=docker/dockerfile:1
ARG JAVA_VERSION=25

# ---- Stage 1: build con Maven wrapper ----
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
WORKDIR /app

# Prima copiamo solo i file necessari a risolvere le dipendenze: massimizza la cache dei layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Poi il sorgente e il package vero e proprio.
COPY src/ src/
# In CI i test girano sul runner (Testcontainers ha accesso al socket Docker),
# quindi qui di solito passiamo SKIP_TESTS=true per non rieseguirli.
ARG SKIP_TESTS=false
RUN ./mvnw -B -ntp clean package -DskipTests=${SKIP_TESTS}

# ---- Stage 2: runtime snello ----
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime
WORKDIR /app

ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}
LABEL org.opencontainers.image.title="githubresumer" \
      org.opencontainers.image.version="${APP_VERSION}"

# Esegui come utente non-root.
RUN useradd -r -u 1001 spring
USER spring

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
