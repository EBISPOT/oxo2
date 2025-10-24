#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Ensure SOLR_HOME is set and exists
if [ -z "$SOLR_HOME" ]; then
  echo "SOLR_HOME is not set. Please set the environment variable and retry." >&2
  exit 1
fi

mkdir -p $SOLR_HOME

# Delete previous config
rm -r $SOLR_HOME/oxo2-mappings
rm -r $SOLR_HOME/oxo2-mappingsets

# Copy config
cp -r $SCRIPT_DIR/solr-config/solr.xml $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/zoo.cfg $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/oxo2-mappings $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/oxo2-mappingsets $SOLR_HOME