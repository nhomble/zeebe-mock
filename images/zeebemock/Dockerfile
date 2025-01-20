FROM maven:3.9.6-eclipse-temurin-21-jammy as build
ENV MAVEN_REPO=/app/.m2

WORKDIR /app
COPY . .

RUN mvn dependency:go-offline -Dmaven.repo.local=${MAVEN_REPO} --quiet
RUN mvn clean install -Dmaven.repo.local=${MAVEN_REPO} --quiet -Dmaven.test.skip -DskipTests -Dfmt.skip
RUN mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -Dmaven.repo.local=${MAVEN_REPO} --quiet

FROM eclipse-temurin:21 as runtime

WORKDIR /app
COPY --from=build /app .
COPY ./images/zeebemock/docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

ENTRYPOINT ["./docker-entrypoint.sh"]
