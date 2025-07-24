#!/bin/bash

# Bring docker container down
docker compose down

# Remove all oxo2 images
docker rmi -f $(docker images --format "{{.Repository}}" | grep "^oxo2")

# Remove all oxo2 volumes
docker volume rm $(docker volume ls --format "{{.Name}}" | grep "^oxo2")