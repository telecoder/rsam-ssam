#!/bin/bash

# Change the path to the location of your Java executable
JAVA_PATH="/usr/bin/java"

# In order to allow multiple rsam-ssam instances running in the same host, an
# unique ID can be added at the end, ex: main_instance.
RSAM_SSAM="rsam-ssam.jar main_instance"

case "$1" in
  start)
    $JAVA_PATH -jar $RSAM_SSAM &
    ;;
  stop)
    # Find the process ID of the program
    PID=$(pgrep -f "$RSAM_SSAM")
    kill $PID
    ;;
  *)
    echo "Usage: $0 {start|stop}"
esac
