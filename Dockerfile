FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean verify

FROM curlimages/curl:8.15.0 AS otel-agent
ARG OTEL_AGENT_VERSION=2.33.1
RUN curl --fail --location --retry 3 \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    --output /tmp/opentelemetry-javaagent.jar \
    && test -s /tmp/opentelemetry-javaagent.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/transaction-lab-1.0.0.jar /app/app.jar
COPY --from=otel-agent /tmp/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar" \
    OTEL_SERVICE_NAME="transaction-lab" \
    OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf" \
    OTEL_LOGS_EXPORTER="none"
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
