FROM maven:3.9.6-eclipse-temurin-21-jammy AS build

WORKDIR /app
COPY . .
RUN mvn clean package --quiet -Dmaven.test.skip -DskipTests -Dfmt.skip

FROM wiremock/wiremock:3.10.0

COPY --from=build /app/camunda-client-wiremock-extension/target/camunda-client-wiremock-extension-1.0-SNAPSHOT-shaded.jar /var/wiremock/extensions/camunda-client-wiremock-extension.jar
COPY --from=build /app/zeebe-mock-wiremock-extension/target/zeebe-mock-wiremock-extension-1.0-SNAPSHOT-shaded.jar /var/wiremock/extensions/zeebe-mock-wiremock-extension.jar

ENTRYPOINT ["/docker-entrypoint.sh", "--global-response-templating", "--disable-gzip", "--verbose"]