#!/bin/bash

cd "$(dirname "$0")"  # Always run from the script's directory

source ./dockerEnv.sh

#docker compose run --remove-orphans oxo2-maven-build
docker compose up --remove-orphans