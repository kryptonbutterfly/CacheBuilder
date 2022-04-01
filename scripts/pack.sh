#!/bin/bash

buildFolder=../build

zipFile=$buildFolder/CacheASM.zip

rm -f -I $zipFile

cd ../zip

zip -r $zipFile ./*
