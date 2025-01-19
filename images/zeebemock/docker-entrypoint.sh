#!/usr/bin/env sh

CLASSPATH=$(cat "/app/classpath.txt")
APP_CLASSPATH=$(cat "/app/zeebe-mock-worker-standalone/classpath.txt")
java -classpath "/app/zeebe-mock-worker-standalone/target/classes:$CLASSPATH:$APP_CLASSPATH" io.nhomble.zeebemock.ZeebeMock