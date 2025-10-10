#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Delete previous config
rm -r $SOLR_HOME/oxo2-mappings
rm -r $SOLR_HOME/oxo2-mappingsets

# Copy config
cp -r $SCRIPT_DIR/solr-config/solr.xml $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/zoo.cfg $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/oxo2-mappings $SOLR_HOME
cp -r $SCRIPT_DIR/solr-config/oxo2-mappingsets $SOLR_HOME