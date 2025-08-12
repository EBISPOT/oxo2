#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Delete previous config
rm -r $SOLR_HOME/server/solr/oxo2-mappings
rm -r $SOLR_HOME/server/solr/oxo2-mappingsets

# Copy config
cp -r $SCRIPT_DIR/solr-config/oxo2-mappings $SOLR_HOME/server/solr
cp -r $SCRIPT_DIR/solr-config/oxo2-mappingsets $SOLR_HOME/server/solr