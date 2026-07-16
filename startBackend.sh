#!/bin/bash

SCRIPT_PATH=$(dirname $(readlink -f $0))

echo OXO2_SOLR_HOST=$OXO2_SOLR_HOST

# Only override solr.url / server.port when the corresponding env var is set and non-empty.
# Passing an empty -Dsolr.url= would blank out the application.properties default and yield a
# schemeless base URL ("URI with undefined scheme") when the Solr client is built.
#
# Kubernetes service links (enableServiceLinks, on by default) inject
# OXO2_BACKEND_PORT=tcp://<cluster-ip>:<port> into every pod in a namespace with a Service
# named oxo2-backend, so only forward the variable when it is a plain port number.
case "$OXO2_BACKEND_PORT" in
    ''|*[!0-9]*) OXO2_BACKEND_PORT="" ;;
esac
java -Xms1024m -Xmx8192m ${OXO2_SOLR_HOST:+-Dsolr.url=$OXO2_SOLR_HOST} ${OXO2_BACKEND_PORT:+-Dserver.port=$OXO2_BACKEND_PORT} -jar $SCRIPT_PATH/oxo2-backend/target/oxo2-backend-1.0.0-SNAPSHOT.jar
