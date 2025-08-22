#!/bin/bash

./gradlew build -x test

APPLICATIONINSIGHTS_CONNECTION_STRING="<my connection string>" \
AZURE_HTTP_LOG_DETAIL_LEVEL=BODY_AND_HEADERS \
java -noverify -Xverify:none \
-javaagent:agent/agent/build/libs/applicationinsights-agent-3.7.2-SNAPSHOT.jar \
-cp agent/agent/build/libs/applicationinsights-agent-3.7.2-SNAPSHOT.jar \
com.microsoft.applicationinsights.agent.internal.ManualProfileTest
