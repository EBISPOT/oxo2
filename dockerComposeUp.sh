#!/bin/bash

export USER_ID=$(id -u)
export GROUP_ID=$(id -g)

export OXO_CONFIG=./oxo-config.json

docker compose --verbose -f docker-compose.yml -f ./oxo2-dataload/docker-compose.yml up