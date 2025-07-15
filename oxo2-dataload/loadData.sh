#!/bin/bash
cd oxo2-dataload
./downloadMappings.sh ../oxo-config.json $OXO2_DATA/sssom
./sssom2json.sh $OXO2_DATA/sssom $OXO2_DATA/sssom-as-json
./makeInferences.sh
./json2solr $OXO2_DATA/sssom-as-json/mapping http://localhost:8983/solr/oxo2-mappings
./json2solr $OXO2_DATA/sssom-as-json/mappingSet http://localhost:8983/solr/oxo2-mappingsets
cd ..