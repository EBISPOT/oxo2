#!/bin/bash

SCRIPT_PATH=$(dirname $(readlink -f $0))

java -Xms1024m -Xms8192m -jar $SCRIPT_PATH/oxo2-backend/target/oxo2-backend-1.0.0-SNAPSHOT.jar
