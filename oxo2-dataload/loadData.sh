#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo SCRIPT_DIR=$SCRIPT_DIR
echo OXO2_DATA=$OXO2_DATA
echo OXO2_CONFIG=$OXO2_CONFIG

OXO2_INFERENCES=$OXO2_DATA/inferences
export OXO2_INFERENCES
rm -R $OXO2_DATA/*


start_time=$(date +%s)
echo "###################" downloadMappings...
mkdir -p $OXO2_DATA/sssom
$SCRIPT_DIR/downloadMappings.sh $OXO2_CONFIG $OXO2_DATA/sssom
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Download took: ${elapsed} seconds"

mkdir -p $OXO2_DATA/nextflow_work

if command -v nextflow &> /dev/null; then
    echo "Using Nextflow for parallel processing of SSSOM to JSON conversion"

    start_time=$(date +%s)
    nextflow -c $SCRIPT_DIR/nextflow/nextflow.config run $SCRIPT_DIR/sssom2json.nf 

    end_time=$(date +%s)
    elapsed=$((end_time - start_time))
    echo "Making inferences using Nextflow Parallel Processing: Writing JSON took: ${elapsed} seconds"
else
    echo "Nextflow not found, using Sequential Processing of SSSOM to JSON conversion"
    start_time=$(date +%s)
    $SCRIPT_DIR/sssom2json.sh $OXO2_DATA/sssom $OXO2_DATA/sssom-as-json
    end_time=$(date +%s)
    elapsed=$((end_time - start_time))
    echo "Using Sequential Processing: Writing JSON took: ${elapsed} seconds"
fi


start_time=$(date +%s)
echo  "###################"  makeInferences...
$SCRIPT_DIR/determineInferencesAndExplanations.sh
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Total inference process took: ${elapsed} seconds"

echo  "###################"  copySolrConfig...
$SCRIPT_DIR/copySolrConfig.sh
sleep 2
echo Start solr ...
mkdir -p $OXO2_DATA/tmp
$SOLR_SCRIPT/solr start --user-managed -Djava.io.tmpdir=$OXO2_DATA/tmp


sleep 10
start_time=$(date +%s)
echo  "###################"  json2solr mappings ...
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mapping http://localhost:8983/solr/oxo2-mappings
echo  "###################"  json2solr mappingSets ...
$SCRIPT_DIR/json2solr.sh $OXO2_DATA/sssom-as-json/mappingSet http://localhost:8983/solr/oxo2-mappingsets
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Writing to Solr took: ${elapsed} seconds"
sleep 20


mkdir -p $OXO2_INFERENCES/solr

if command -v nextflow &> /dev/null; then                                                                                       
    echo "Using Nextflow for parallel explanations2json"                                                                        
    start_time=$(date +%s)                                                                                                      
    nextflow -c $SCRIPT_DIR/nextflow/nextflow.config run $SCRIPT_DIR/oxo2-json2inferences/explanations2json.nf                  
    end_time=$(date +%s)                                                                                                        
    elapsed=$((end_time - start_time))                                                                                          
    echo "Using Nextflow Parallel Processing: explanations2json took: ${elapsed} seconds"                                       
else                                                                                                                            
    echo "Nextflow not found, using sequential processing"                                                                      
    $SCRIPT_DIR/oxo2-json2inferences/explanations2json.sh "$OXO2_INFERENCES/inferenceChains"                                    
"$OXO2_INFERENCES/solr"                                                                                           
fi


 echo  "###################"  json2solr inferred mappings ...
 $SCRIPT_DIR/json2solr.sh $OXO2_INFERENCES/solr http://localhost:8983/solr/oxo2-mappings
 sleep 20


echo  "################### Stopping solr ..."
$SOLR_SCRIPT/solr stop

#Important to ensure docker does not experience errors related to write.lock.
chmod -R 777 $SOLR_HOME/*
cd ..