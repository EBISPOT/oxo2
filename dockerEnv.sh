#!/bin/bash

# To use local repo to speed up build
export M2_VOLUME="$HOME/.m2/repository"
# To use named volume to speed up build
# export M2_VOLUME="maven-repo"

#export USER_ID=$(id -u)
#export GROUP_ID=$(id -g)

export OXO_CONFIG=oxo-config.json

echo "OXO_CONFIG="$OXO_CONFIG
