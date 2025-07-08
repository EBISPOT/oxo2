#!/bin/bash

export OXO_CONFIG=./oxo-config.json

if [[ "$1" == "build" ]]; then
  docker compose build
  exit 0
fi

if [[ "$1" == "rebuild" ]]; then
  docker compose build --no-cache
  exit 0
fi

if [[ "$1" == "clean" ]]; then
  docker compose down -v
  exit 0
fi

# Default: run containers (reuse build/artifacts)
docker compose up --remove-orphans
