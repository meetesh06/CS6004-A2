#!/bin/bash

FILENAME="23d0361-pa2"

rm -rf "$FILENAME.zip" &>/dev/null
rm -rf "$FILENAME" &>/dev/null

mkdir "$FILENAME"

cp PA2.java "$FILENAME"
cp PointRecorder.java "$FILENAME"
cp PointsToGraph.java "$FILENAME"
cp AnalysisTransformer.java "$FILENAME"

zip -r "$FILENAME.zip" $FILENAME
