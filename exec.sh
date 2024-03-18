#!/bin/bash

export JAVA_HOME=/home/mee/dev/CS60004/A1/jdk1.8.0_401
export PATH=/home/mee/dev/CS60004/A1/jdk1.8.0_401/bin:$PATH

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{sub("^$", "0", $2); print $1$2}')

if [ "$JAVA_VER" -eq 18 ]; then
    echo "USING JAVA VERSION: $JAVA_VER"
else
    echo "UNSUPPORTED JAVA VERSION: $JAVA_VER, expected Java 18."
    exit 1    
fi

cd testcase
rm *.class &>/dev/null
javac *.java &>/dev/null
cd ..

rm -rf recording &>/dev/null
rm *.class &>/dev/null
rm *.DOT &>/dev/null
rm *.jimple &>/dev/null

javac -cp .:sootclasses-trunk-jar-with-dependencies.jar:testcase *.java
java -cp .:sootclasses-trunk-jar-with-dependencies.jar:testcase PA2
