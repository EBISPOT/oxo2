#!/bin/bash

export USER_ID=$(id -u)
export GROUP_ID=$(id -g)

export OXO_CONFIG=./../oxo-config.json
export JAVA_OPTS="-Xms5G -Xmx10G"

docker compose --verbose up