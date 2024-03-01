#!/bin/bash

export JAVA_HOME=/home/mee/dev/CS60004/A1/jdk1.8.0_401
export PATH=/home/mee/dev/CS60004/A1/jdk1.8.0_401/bin:$PATH

rm -rf recording
rm *.class
rm *.DOT
rm *.jimple

javac -cp .:sootclasses-trunk-jar-with-dependencies.jar *.java
java -cp .:sootclasses-trunk-jar-with-dependencies.jar PA2
