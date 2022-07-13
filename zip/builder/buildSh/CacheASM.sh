#!/bin/bash

# echo "running at: " $0
jarLocation=$(dirname "$0")/../buildJars/CacheASM.jar
# echo $jarLocation
java -jar $jarLocation $@
