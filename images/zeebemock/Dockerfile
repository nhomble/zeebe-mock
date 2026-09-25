FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
ENV MAVEN_REPO=/app/.m2

WORKDIR /app

# copy only the poms first so dependency resolution is cached across builds
# whose source changed but whose dependencies didn't
COPY pom.xml .
COPY zeebe-mock/pom.xml zeebe-mock/pom.xml
COPY zeebe-mock-worker-standalone/pom.xml zeebe-mock-worker-standalone/pom.xml
COPY camunda-client-wiremock-extension/pom.xml camunda-client-wiremock-extension/pom.xml
COPY zeebe-mock-wiremock-extension/pom.xml zeebe-mock-wiremock-extension/pom.xml
COPY integration-tests/pom.xml integration-tests/pom.xml
RUN mvn dependency:go-offline -Dmaven.repo.local=${MAVEN_REPO} --quiet

COPY . .
RUN mvn clean install -Dmaven.repo.local=${MAVEN_REPO} --quiet -Dmaven.test.skip -DskipTests -Dfmt.skip
RUN mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -Dmaven.repo.local=${MAVEN_REPO} --quiet

FROM eclipse-temurin:21 AS runtime

WORKDIR /app
COPY --from=build /app .
COPY ./images/zeebemock/docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

ENTRYPOINT ["./docker-entrypoint.sh"]
