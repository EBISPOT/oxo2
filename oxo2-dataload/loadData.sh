#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo SCRIPT_DIR=$SCRIPT_DIR
echo OXO2_DATA=$OXO2_DATA
echo OXO2_CONFIG=$OXO2_CONFIG

rm -R $OXO2_DATA/*

start_time=$(date +%s)
echo downloadMappings...
$SCRIPT_DIR/downloadMappings.sh $OXO2_CONFIG $OXO2_DATA/sssom
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Download took: ${elapsed} seconds"

start_time=$(date +%s)
echo sssom2json...
$SCRIPT_DIR/sssom2json.sh $OXO2_DATA/sssom $OXO2_DATA/sssom-as-json
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "SSSOM2JSON took: ${elapsed} seconds"

start_time=$(date +%s)
echo makeInferences...
$SCRIPT_DIR/determineInferencesAndExplanations.sh
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Total inference process took: ${elapsed} seconds"

echo copySolrConfig...
$SCRIPT_DIR/copySolrConfig.sh
sleep 2
echo Start solr ...
$SOLR_HOME/bin/solr start --user-managed

sleep 10
start_time=$(date +%s)
echo json2solr mappings ...
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mapping http://localhost:8983/solr/oxo2-mappings
echo json2solr mappingSets
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mappingSet http://localhost:8983/solr/oxo2-mappingsets
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Writing to Solr took: ${elapsed} seconds"
sleep 10

OXO2_INFERENCES=$OXO2_DATA/inferences
mkdir -p $OXO2_INFERENCES/solr

# Write out inferred mappings with their explanations.
start_time=$(date +%s)
$SCRIPT_DIR/oxo2-json2inferences/explanations2json.sh $OXO2_INFERENCES/inferences-chains.json \
$OXO2_INFERENCES/solr/inferred-mappings.json
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Interpreting explanations took: ${elapsed} seconds"

echo json2solr inferred mappings ...
$SCRIPT_DIR/json2solr.sh $OXO2_INFERENCES/solr http://localhost:8983/solr/oxo2-mappings

$SOLR_HOME/bin/solr stop

cd ..