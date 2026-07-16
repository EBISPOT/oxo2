#!/bin/bash

SCRIPT_PATH=$(dirname $(readlink -f $0))

echo OXO2_SOLR_HOST=$OXO2_SOLR_HOST

# Only override solr.url / server.port when the corresponding env var is set and non-empty.
# Passing an empty -Dsolr.url= would blank out the application.properties default and yield a
# schemeless base URL ("URI with undefined scheme") when the Solr client is built.
java -Xms1024m -Xmx8192m ${OXO2_SOLR_HOST:+-Dsolr.url=$OXO2_SOLR_HOST} ${OXO2_BACKEND_PORT:+-Dserver.port=$OXO2_BACKEND_PORT} -jar $SCRIPT_PATH/oxo2-backend/target/oxo2-backend-1.0.0-SNAPSHOT.jar
