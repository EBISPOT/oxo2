#!/bin/bash

export OXO_CONFIG=./../oxo-config.json
export JAVA_OPTS="-Xms5G -Xmx10G"

docker compose --progress plain build