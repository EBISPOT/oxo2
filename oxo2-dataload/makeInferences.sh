#!/bin/bash

## Generate ttl from json files
./oxo2-json2inferences/json2ttl.sh ../../data/sssom_as_json/mapping ../../data/assertedMapping.ttl

## Run nmo to get initial inferences
# -o: overwrites existing files
# -e idb: trace idb inferences. See https://github.com/knowsys/nemo/issues/670
# -v: show progress of inferencing. See https://github.com/knowsys/nemo/issues/676
./oxo2-json2inferences/nmo ./oxo2-json2inferences/chain-rules.rls -o -v -D ../../data/

## Determine mappings that for which we want explanations
./oxo2-json2inferences/inferences2trace.sh ../../data/inferredMapping.ttl ../../data/facts2trace.txt

## Get explanations for facts to trace from nmo.
## IDB - Intensional Database Predicates are those that appear in the head of a rule, i.e. (head <- tail)
./oxo2-json2inferences/nmo ./oxo2-json2inferences/chain-rules.rls -o -v  -D ./test/results \
 --trace-input-file ../../data/inferencesToTrace.txt \
 --trace-output ../../data/inferences-chains.json

## Write out inferred mappings with their explanations.
./oxo2-json2inferences/explanations2json.sh ../../data/inferences-chains.json ../../data/sssom_as_json/mapping ../../data/sssom_as_json/inferred-mappings.json

