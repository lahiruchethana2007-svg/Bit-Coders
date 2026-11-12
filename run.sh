#!/bin/sh
mkdir -p out
javac -d out java/*.java && java -cp out EcoNestServer
