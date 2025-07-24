#!/bin/bash

export OXO_CONFIG=./oxo-config.json

COMPOSE_FILE="docker-compose.yml"
if [[ -n "$2" ]]; then
  COMPOSE_FILE="$2"
fi

if [[ "$1" == "build" ]]; then
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

docker compose -f "$COMPOSE_FILE" up --remove-orphans
