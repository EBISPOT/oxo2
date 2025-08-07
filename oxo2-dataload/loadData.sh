#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo SCRIPT_DIR=$SCRIPT_DIR
echo OXO2_DATA=$OXO2_DATA
echo OXO2_CONFIG=$OXO2_CONFIG

echo downloadMappings...
$SCRIPT_DIR/downloadMappings.sh $OXO2_CONFIG $OXO2_DATA/sssom
echo sssom2json...
$SCRIPT_DIR/sssom2json.sh $OXO2_DATA/sssom $OXO2_DATA/sssom-as-json

echo makeInferences...
$SCRIPT_DIR/makeInferences.sh

echo copySolrConfig...
$SCRIPT_DIR/copySolrConfig.sh
sleep 2
echo Start solr ...
$SOLR_HOME/bin/solr start --user-managed

sleep 10
echo json2solr mappings ...
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mapping http://localhost:8983/solr/oxo2-mappings
echo json2solr mappingSets
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mappingSet http://localhost:8983/solr/oxo2-mappingsets
sleep 10

$SOLR_HOME/bin/solr stop

cd ..