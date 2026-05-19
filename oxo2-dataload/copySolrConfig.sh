#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${SOLR_HOME:?SOLR_HOME must be set. Please set the environment variable and retry.}"

mkdir -p "$SOLR_HOME"

# Delete previous config
rm -rf "$SOLR_HOME/oxo2-mappings" "$SOLR_HOME/oxo2-mappingsets"

# Copy config
cp "$SCRIPT_DIR/solr-config/solr.xml" "$SOLR_HOME/solr.xml"
cp "$SCRIPT_DIR/solr-config/zoo.cfg" "$SOLR_HOME/zoo.cfg"
cp -r "$SCRIPT_DIR/solr-config/oxo2-mappings" "$SOLR_HOME/oxo2-mappings"
cp -r "$SCRIPT_DIR/solr-config/oxo2-mappingsets" "$SOLR_HOME/oxo2-mappingsets"
