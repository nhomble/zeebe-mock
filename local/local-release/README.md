# local release testing

There are a lot of steps to publishing maven artifacts. Use the `local-release/` testing project to debug the process.

`mvn clean deploy -s local/local-release/settings.xml -DskipTests=true -X`