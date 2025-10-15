#!/bin/bash

SCRIPT_PATH=$(dirname $(readlink -f $0))

echo OXO2_SOLR_HOST=$OXO2_SOLR_HOST

java -Xms1024m -Xms8192m -Dsolr.url=$OXO2_SOLR_HOST -jar $SCRIPT_PATH/oxo2-backend/target/oxo2-backend-1.0.0-SNAPSHOT.jar
