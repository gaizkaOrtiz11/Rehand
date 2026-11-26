# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the jar with Maven -------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
# Download dependencies first so rebuilds are fast when only the sources change
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Run stage: small JRE image ---------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin rehand \
    && mkdir -p /app/uploads \
    && chown -R rehand:rehand /app
COPY --from=build /build/target/rehand.jar /app/rehand.jar
USER rehand
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/rehand.jar"]
