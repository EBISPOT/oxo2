#!/bin/bash

export OXO2_CONFIG=/oxo-config-test.json

COMPOSE_FILE="docker-compose.yml"
if [[ -n "$2" ]]; then
  COMPOSE_FILE="$2"
fi

if [[ "$1" == "build" ]]; then
#  docker build -t oxo2-maven-build:latest .
  docker compose -f "$COMPOSE_FILE" build
  exit 0
fi

if [[ "$1" == "rebuild" ]]; then
  docker compose -f "$COMPOSE_FILE" build --no-cache
  exit 0
fi

if [[ "$1" == "clean" ]]; then
  docker compose -f "$COMPOSE_FILE" down -v
  exit 0
fi


if [[ "$1" == "term" ]]; then
  docker compose exec build /bin/bash
  exit 0
fi

docker compose -f "$COMPOSE_FILE" up --remove-orphans
