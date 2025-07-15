#!/bin/bash

source ./dockerEnv.sh

docker compose images
#docker compose exec oxo2-maven-build /bin/bash