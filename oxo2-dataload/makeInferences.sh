#!/bin/bash
OXO2_INFERENCES=$OXO2_DATA/inferences

## Generate ttl from json files
./oxo2-json2inferences/json2ttl.sh $OXO2_DATA/sssom_as_json/mapping $OXO2_INFERENCES/assertedMapping.ttl

## Run nmo to get initial inferences
# -o: overwrites existing files
# -e idb: trace idb inferences. See https://github.com/knowsys/nemo/issues/670
# -v: show progress of inferencing. See https://github.com/knowsys/nemo/issues/676
nmo ./oxo2-json2inferences/chain-rules.rls -o -v -D $OXO2_INFERENCES

## Determine mappings that for which we want explanations
./oxo2-json2inferences/inferences2trace.sh $OXO2_INFERENCES/inferredMapping.ttl $OXO2_INFERENCES/inferencesToTrace.txt

## Get explanations for facts to trace from nmo.
## IDB - Intensional Database Predicates are those that appear in the head of a rule, i.e. (head <- tail)
nmo ./oxo2-json2inferences/chain-rules.rls -o -v  -D $OXO2_INFERENCES \
--trace-input-file $OXO2_INFERENCES/inferencesToTrace.txt \
--trace-output $OXO2_INFERENCES/inferences-chains.json

## Write out inferred mappings with their explanations.
./oxo2-json2inferences/explanations2json.sh $OXO2_INFERENCES/inferences-chains.json $OXO2_DATA/sssom_as_json/mapping $OXO2_DATA/sssom_as_json/mapping/inferred-mappings.json
