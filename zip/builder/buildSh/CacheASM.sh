#!/bin/bash

# echo "running at: " $0
jarLocation=$(dirname "$0")/../buildJars/CacheASM.jar
# echo $jarLocation
/usr/lib/jvm/java-1.17.0-openjdk-amd64/bin/java -jar $jarLocation $@
