#!/bin/bash

## Generate ttl from json files
./oxo2-json2rules/json2ttl.sh ../../data/sssom_as_json/mapping ../../data/asserted_mappings.ttl

## Run nmo to get initial inferences
./oxo2-json2rules/nmo ./oxo2-json2rules/chain-rules.rls -o -v -D ../../data/

## Determine mappings that for which we want explanations
./oxo2-json2rules/inferences2trace.sh ../../data/asserted_mappings.ttl ../../data/mapping.ttl ../../data/facts2trace.txt

## Get explanations for facts to trace from nmo.
## IDB - Intensional Database Predicates are those that appear in the head of a rule, i.e. (head <- tail)
./oxo2-json2rules/nmo ./oxo2-json2rules/chain-rules.rls -o -v -e idb -D ./test/results \
 --trace-input-file ../../data/facts2trace.txt \
 --trace-output ../../data/inferences-chains.json

## Write out inferred mappings with their explanations.
./oxo2-json2rules/inferences2json.sh ../../data/inferences-chains.json ../../data/sssom_as_json/inferred-mappings.json

