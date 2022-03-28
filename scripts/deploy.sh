#!/bin/bash

buildJarsDir=/home/christian/eclipse-photon/builder/buildJars
buildScriptsDir=/home/christian/eclipse-photon/builder/buildSh

mkdir -p $buildJarsDir
mkdir -p $buildScriptsDir

cp ../build/CacheASM.jar $buildJarsDir
cp CacheASM.sh $buildScriptsDir
