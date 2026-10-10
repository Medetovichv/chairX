FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system chairx \
    && useradd --system --gid chairx --home-dir /app chairx
WORKDIR /app
COPY --from=build --chown=chairx:chairx /workspace/target/chairx-0.0.1-SNAPSHOT.jar ./chairx.jar
USER chairx
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/chairx.jar"]

