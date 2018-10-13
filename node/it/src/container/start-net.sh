#!/bin/bash

trap 'kill -TERM $PID' TERM INT
echo Options: $MIR_OPTS
java $MIR_OPTS -jar /opt/waves/waves.jar /opt/waves/template.conf &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
