#!/bin/bash

eclipseInstallDir=$1
zipFile=$2

# echo $zipFile -d $eclipseInstallDir

unzip -qq $zipFile -d $eclipseInstallDir
